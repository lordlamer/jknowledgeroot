import { Editor, Extension, getSchema } from '@tiptap/core';
import StarterKit from '@tiptap/starter-kit';
import { TableKit } from '@tiptap/extension-table';
import Image from '@tiptap/extension-image';
import { TextStyleKit } from '@tiptap/extension-text-style';
import TextAlign from '@tiptap/extension-text-align';
import Collaboration from '@tiptap/extension-collaboration';
import CollaborationCaret from '@tiptap/extension-collaboration-caret';
import { prosemirrorToYXmlFragment, ySyncPluginKey } from '@tiptap/y-tiptap';
import { DOMParser as ProseMirrorDOMParser } from '@tiptap/pm/model';
import * as Y from 'yjs';
import { Awareness, applyAwarenessUpdate, encodeAwarenessUpdate, removeAwarenessStates } from 'y-protocols/awareness';
import DOMPurify from 'dompurify';
import './editor.css';

const instances = new Map();
// Interface texts come from window.krT (page head); the fallbacks keep the bundle usable on its own.
const t = (key, fallback, ...args) => window.krT ? window.krT(key, fallback, ...args)
  : args.reduce((text, arg, i) => text.split(`{${i}}`).join(String(arg)), fallback);
const clean = html => DOMPurify.sanitize(html, {
  USE_PROFILES: { html: true },
  FORBID_TAGS: ['style', 'form', 'input', 'button', 'iframe', 'object', 'embed', 'video', 'audio'],
  FORBID_ATTR: ['srcdoc'], ALLOW_DATA_ATTR: false
});
const safeUrl = value => {
  try { return ['http:', 'https:', 'mailto:'].includes(new URL(value, location.href).protocol); }
  catch { return false; }
};

// Older content may put font/color styles directly on blocks rather than span marks.
const ExistingBlockStyles = Extension.create({
  name: 'existingBlockStyles',
  addGlobalAttributes() {
    return [{ types: ['paragraph', 'heading', 'tableCell', 'tableHeader'], attributes: {
      existingStyle: {
        default: null,
        parseHTML: element => ['color', 'background-color', 'font-family', 'font-size']
          .map(property => [property, element.style.getPropertyValue(property)])
          .filter(([, value]) => value && !/url\s*\(|expression\s*\(/i.test(value))
          .map(([property, value]) => `${property}:${value}`).join(';') || null,
        renderHTML: attributes => attributes.existingStyle ? { style: attributes.existingStyle } : {}
      }
    } }];
  }
});

// Live collaboration (R63). One WebSocket per open editor relays Yjs updates and awareness
// (carets, names) between the browsers of a page; the server stores the updates and replays them
// to whoever joins. Frame layout: first byte = type, see CollaborationHandler on the server.
const FRAME = { update: 0, awareness: 1, snapshot: 2, ready: 3, peerJoined: 4, snapshotRequested: 5, published: 6 };
const CARET_COLORS = ['#2563eb', '#db2777', '#059669', '#d97706', '#7c3aed', '#dc2626', '#0891b2', '#4d7c0f'];
const caretColor = name => CARET_COLORS[[...String(name)].reduce((sum, char) => sum + char.charCodeAt(0), 0) % CARET_COLORS.length];

class LiveProvider {
  constructor(url, doc, callbacks) {
    this.url = url;
    this.doc = doc;
    this.awareness = new Awareness(doc);
    this.callbacks = callbacks;
    this.lastId = 0n;
    this.attempts = 0;
    this.ready = false;
    this.destroyed = false;
    this.onDocUpdate = (update, origin) => { if (origin !== this) this.send(FRAME.update, update); };
    this.onAwareness = ({ added, updated, removed }) => {
      if ([...added, ...updated, ...removed].includes(doc.clientID)) this.announce();
    };
    doc.on('update', this.onDocUpdate);
    this.awareness.on('update', this.onAwareness);
    this.connect();
  }

  connect() {
    if (this.destroyed) return;
    this.callbacks.status('connecting');
    const socket = new WebSocket(this.url);
    socket.binaryType = 'arraybuffer';
    socket.onopen = () => { this.attempts = 0; };
    socket.onmessage = event => this.receive(new Uint8Array(event.data));
    socket.onclose = event => {
      this.ready = false;
      if (this.destroyed) return;
      if (event.code === 4403) this.callbacks.status('lost');
      else if (event.code === 4409) this.callbacks.status('cleared');
      else if (this.attempts < 5) {
        this.attempts += 1;
        this.callbacks.status('connecting');
        setTimeout(() => this.connect(), 1000 * this.attempts);
      } else this.callbacks.status('offline');
    };
    socket.onerror = () => { /* onclose follows and decides */ };
    this.socket = socket;
  }

  receive(bytes) {
    const type = bytes[0];
    const view = new DataView(bytes.buffer, bytes.byteOffset, bytes.byteLength);
    if (type === FRAME.update) {
      this.lastId = view.getBigUint64(1);
      Y.applyUpdate(this.doc, bytes.subarray(9), this);
    } else if (type === FRAME.awareness) {
      applyAwarenessUpdate(this.awareness, bytes.subarray(1), this);
      this.peers();
    } else if (type === FRAME.ready) {
      this.lastId = view.getBigUint64(1);
      this.ready = true;
      this.callbacks.ready(bytes[9] === 1);
      this.announce();
      this.callbacks.status('live');
      this.peers();
    } else if (type === FRAME.peerJoined) {
      this.announce();
    } else if (type === FRAME.published) {
      this.callbacks.published(Number(view.getBigUint64(1)), new TextDecoder().decode(bytes.subarray(9)));
    } else if (type === FRAME.snapshotRequested) {
      const upTo = new Uint8Array(8);
      new DataView(upTo.buffer).setBigUint64(0, this.lastId);
      const state = Y.encodeStateAsUpdate(this.doc);
      const payload = new Uint8Array(8 + state.length);
      payload.set(upTo, 0);
      payload.set(state, 8);
      this.send(FRAME.snapshot, payload);
    }
  }

  send(type, bytes) {
    if (!this.socket || this.socket.readyState !== WebSocket.OPEN || (type !== FRAME.awareness && !this.ready)) return;
    const frame = new Uint8Array(1 + bytes.length);
    frame[0] = type;
    frame.set(bytes, 1);
    this.socket.send(frame);
  }

  announce() {
    this.send(FRAME.awareness, encodeAwarenessUpdate(this.awareness, [this.doc.clientID]));
  }

  /** The other editors of the page (their caret users: name and colour), for the status line. */
  peers() {
    const others = [];
    for (const [client, state] of this.awareness.getStates()) {
      if (client !== this.doc.clientID && state && state.user) others.push(state.user);
    }
    this.callbacks.peers(others);
  }

  destroy() {
    this.destroyed = true;
    // Announce the removed caret first (the listener sends it), then stop listening.
    removeAwarenessStates(this.awareness, [this.doc.clientID], 'destroy');
    this.doc.off('update', this.onDocUpdate);
    this.awareness.off('update', this.onAwareness);
    if (this.socket) {
      try { this.socket.close(1000); } catch { /* already closed */ }
    }
  }
}

function initialize(root) {
  const fields = root.matches?.('textarea[data-rich-editor]') ? [root] : root.querySelectorAll('textarea[data-rich-editor]');
  for (const textarea of fields) {
    if (instances.has(textarea) || textarea.knowledgerootMounted) continue;
    textarea.knowledgerootMounted = true;
    const shell = document.createElement('div');
    shell.className = 'kr-rich-editor';
    const toolbar = document.createElement('div');
    toolbar.className = 'kr-rich-toolbar';
    toolbar.setAttribute('role', 'toolbar');
    toolbar.setAttribute('aria-label', t('editor.toolbar', 'Text formatting'));
    const surface = document.createElement('div');
    shell.append(toolbar, surface);
    textarea.before(shell);
    const fail = error => {
      shell.remove();
      textarea.hidden = false;
      if (textarea.knowledgerootLive) textarea.knowledgerootLive.destroy();
      delete textarea.knowledgerootLive;
      console.error('Rich text editor initialization failed', error);
    };
    try {
      const buttons = [];
      const refresh = editor => buttons.forEach(({ button, active }) => {
        if (active) button.setAttribute('aria-pressed', String(active(editor)));
      });
      // Live collaboration (R63): the form names the socket path and the user; without it the editor works alone.
      const form = textarea.form;
      const livePath = form && form.dataset.collab && typeof WebSocket === 'function' ? form.dataset.collab : null;
      const user = form && form.dataset.collabUser ? form.dataset.collabUser : 'guest';
      const liveStatus = form ? form.querySelector('[data-live-status]') : null;
      const liveText = liveStatus ? liveStatus.querySelector('[data-live-text]') : null;
      const livePeers = liveStatus ? liveStatus.querySelector('[data-live-peers]') : null;
      const liveNotice = form ? form.querySelector('[data-live-notice]') : null;
      let provider = null, ydoc = null, editor = null, editorLive = false;
      const starterKit = { link: { openOnClick: false, defaultProtocol: 'https', protocols: ['http', 'https', 'mailto'] } };
      const extensions = [
        TableKit, Image.configure({ allowBase64: false }), TextStyleKit, ExistingBlockStyles,
        TextAlign.configure({ types: ['heading', 'paragraph'] })
      ];
      // In live mode the editor appears once the document is in sync (or live editing proved unavailable),
      // so nobody edits an empty page while updates are still on their way.
      const buildExtensions = live => {
        const config = { ...starterKit };
        const list = [...extensions];
        if (live) {
          config.undoRedo = false; // Collaboration brings its own undo manager.
          list.push(Collaboration.configure({ document: ydoc }));
          list.push(CollaborationCaret.configure({ provider, user: { name: user, color: caretColor(user) } }));
        }
        return [StarterKit.configure(config), ...list];
      };
      const createEditor = live => {
        if (editor) return editor;
        try {
          surface.textContent = '';
          editor = new Editor({
            element: surface,
            extensions: buildExtensions(live),
            ...(live ? {} : { content: clean(textarea.value) }),
            editorProps: {
              attributes: { 'aria-label': t('editor.content', 'Page content'), role: 'textbox', 'aria-multiline': 'true' },
              transformPastedHTML: clean
            },
            onUpdate: ({ editor }) => { textarea.value = editor.getHTML(); },
            onTransaction: ({ editor }) => refresh(editor)
          });
          editorLive = live;
          textarea.knowledgerootLiveEditing = live;
          editor.on('selectionUpdate', () => {
            heading.value = String(editor.getAttributes('heading').level || 0);
          });
          textarea.knowledgerootEditor = editor;
          instances.set(textarea, editor);
          refresh(editor);
          if (form && form.dataset.autosave) autosave(form, textarea, editor);
          return editor;
        } catch (error) {
          fail(error);
          return null;
        }
      };
      if (livePath) {
        ydoc = new Y.Doc();
        const url = (location.protocol === 'https:' ? 'wss://' : 'ws://') + location.host + livePath;
        let peerUsers = [], liveState = 'connecting', publishedText = null;
        // Who else is here: one chip per login, coloured like that editor's caret.
        const renderPeers = () => {
          if (!livePeers) return;
          livePeers.textContent = '';
          const byName = new Map();
          for (const peer of peerUsers) {
            const name = peer.name || 'guest';
            byName.set(name, { count: (byName.get(name) || { count: 0 }).count + 1, color: peer.color });
          }
          for (const [name, { count, color }] of byName) {
            const chip = document.createElement('span');
            chip.className = 'kr-live-peer';
            chip.style.setProperty('--kr-peer', color || caretColor(name));
            const avatar = document.createElement('span');
            avatar.className = 'kr-live-avatar';
            avatar.setAttribute('aria-hidden', 'true');
            avatar.textContent = name.slice(0, 1).toUpperCase();
            chip.append(avatar, document.createTextNode(count > 1 ? name + ' ×' + count : name));
            livePeers.append(chip);
          }
        };
        const notice = (text, action) => {
          if (!liveNotice) return;
          liveNotice.textContent = '';
          liveNotice.hidden = !text;
          if (!text) return;
          const message = document.createElement('span');
          message.textContent = text;
          liveNotice.append(message);
          if (action) {
            const button = document.createElement('button');
            button.type = 'button';
            button.className = 'kr-btn sm';
            button.textContent = action.label;
            button.addEventListener('click', action.run);
            liveNotice.append(button);
          }
        };
        const reload = () => {
          if (window.htmx) window.htmx.ajax('GET', location.pathname, { target: '#content', swap: 'innerHTML' });
          else location.reload();
        };
        // The room is gone for this browser: what is typed from now on could not be shared or saved safely.
        const endLiveEditing = () => {
          textarea.knowledgerootLiveEditing = false;
          if (editor && editorLive) editor.setEditable(false);
          if (textarea.knowledgerootAutosave) textarea.knowledgerootAutosave.stop();
        };
        const status = state => {
          liveState = state;
          if (liveStatus) {
            liveStatus.dataset.live = state === 'live' ? 'on' : state === 'connecting' ? 'connecting'
              : (state === 'lost' || state === 'cleared') ? 'warn' : 'off';
            (liveText || liveStatus).textContent = state === 'live'
              ? (publishedText || (peerUsers.length
                ? t('collab.live-with', 'Live editing with')
                : t('collab.live', 'Live editing on. You are the only editor right now.')))
              : state === 'connecting' ? t('collab.connecting', 'Connecting live editing…')
              : (state === 'lost' || state === 'cleared') ? t('collab.ended', 'Live editing ended.')
              : t('collab.offline', 'Live editing unavailable. Changes are still saved as a working copy.');
            if (state === 'live') renderPeers();
            else if (livePeers) livePeers.textContent = '';
          }
          if (state === 'lost') {
            endLiveEditing();
            notice(t('collab.lost', 'You may no longer edit this page.'));
          } else if (state === 'cleared') {
            endLiveEditing();
            notice(t('collab.cleared', 'The working copy was discarded or the page was changed outside this editor. Reload the editor to continue.'),
              { label: t('collab.reload', 'Reload editor'), run: reload });
          }
          // Without a live session the editor still has to open, from the working copy in the form.
          if ((state === 'offline' || state === 'lost' || state === 'cleared') && !editor) createEditor(false);
        };
        provider = new LiveProvider(url, ydoc, {
          status,
          peers: users => { peerUsers = users; if (liveState === 'live') status('live'); },
          // Someone published the shared document: this form now targets the new revision and editing goes on.
          published: (revision, login) => {
            if (form.elements.revision) form.elements.revision.value = String(revision);
            form.dataset.autosaveStale = 'false';
            const copy = form.querySelector('.kr-working-copy');
            if (copy) copy.remove();
            publishedText = t('collab.published', '{0} published the page at {1}. Live editing continues.', login || 'guest', new Date().toLocaleTimeString());
            if (liveState === 'live') status('live');
            setTimeout(() => { publishedText = null; status(liveState); }, 10000);
            if (textarea.knowledgerootAutosave) textarea.knowledgerootAutosave.revisionChanged();
          },
          ready: seed => {
            if (editor && !editorLive) { status('offline'); return; }
            // The first browser on an empty document seeds it from the working copy or the page. The content goes
            // into the shared document before the editor binds to it, so the result is exactly the parsed page
            // (no initial empty paragraph from the binding) and every other browser receives the same update.
            if (seed && !editor) {
              const body = new window.DOMParser().parseFromString(clean(textarea.value), 'text/html').body;
              const parsed = ProseMirrorDOMParser.fromSchema(getSchema(buildExtensions(true))).parse(body);
              const fragment = ydoc.getXmlFragment('default');
              ydoc.transact(() => { fragment.delete(0, fragment.length); prosemirrorToYXmlFragment(parsed, fragment); });
            }
            createEditor(true);
          }
        });
        textarea.knowledgerootLive = provider;
        surface.textContent = t('collab.connecting', 'Connecting live editing…');
        setTimeout(() => { if (!editor) { status('offline'); } }, 8000);
      }
      const button = (label, action, active) => {
        const element = document.createElement('button');
        element.type = 'button';
        element.textContent = label;
        element.title = label;
        element.addEventListener('click', () => { if (editor) { action(editor); refresh(editor); } });
        toolbar.append(element);
        buttons.push({ button: element, active });
      };
      const heading = document.createElement('select');
      heading.setAttribute('aria-label', t('editor.block-format', 'Paragraph format'));
      for (let level = 0; level <= 6; level++) heading.add(new Option(level ? t('editor.heading', 'Heading {0}', level) : t('editor.paragraph', 'Paragraph'), level));
      heading.addEventListener('change', () => {
        if (!editor) return;
        const level = Number(heading.value);
        if (level) editor.chain().focus().setHeading({ level }).run();
        else editor.chain().focus().setParagraph().run();
      });
      toolbar.append(heading);
      button(t('editor.bold', 'Bold'), e => e.chain().focus().toggleBold().run(), e => e.isActive('bold'));
      button(t('editor.italic', 'Italic'), e => e.chain().focus().toggleItalic().run(), e => e.isActive('italic'));
      button(t('editor.underline', 'Underline'), e => e.chain().focus().toggleUnderline().run(), e => e.isActive('underline'));
      button(t('editor.strikethrough', 'Strikethrough'), e => e.chain().focus().toggleStrike().run(), e => e.isActive('strike'));
      button(t('editor.bullet-list', 'Bullet list'), e => e.chain().focus().toggleBulletList().run(), e => e.isActive('bulletList'));
      button(t('editor.numbered-list', 'Numbered list'), e => e.chain().focus().toggleOrderedList().run(), e => e.isActive('orderedList'));
      button(t('editor.quote', 'Quote'), e => e.chain().focus().toggleBlockquote().run(), e => e.isActive('blockquote'));
      button(t('editor.code-block', 'Code block'), e => e.chain().focus().toggleCodeBlock().run(), e => e.isActive('codeBlock'));
      button(t('editor.link', 'Link'), e => {
        const url = window.prompt(t('editor.link.prompt', 'Link address'), e.getAttributes('link').href || 'https://');
        if (url === '') e.chain().focus().extendMarkRange('link').unsetLink().run();
        else if (url && safeUrl(url)) e.chain().focus().extendMarkRange('link').setLink({ href: url }).run();
      }, e => e.isActive('link'));
      button(t('editor.image', 'Image'), e => {
        const url = window.prompt(t('editor.image.prompt', 'Image address (HTTPS or relative path)'));
        if (url && safeUrl(url) && !url.toLowerCase().startsWith('mailto:')) e.chain().focus().setImage({ src: url }).run();
      });
      button(t('editor.table', 'Table'), e => e.chain().focus().insertTable({ rows: 3, cols: 3, withHeaderRow: true }).run());
      button(t('editor.add-row', 'Add row'), e => e.chain().focus().addRowAfter().run());
      button(t('editor.add-column', 'Add column'), e => e.chain().focus().addColumnAfter().run());
      button(t('editor.delete-table', 'Delete table'), e => e.chain().focus().deleteTable().run());
      button(t('editor.align-left', 'Align left'), e => e.chain().focus().setTextAlign('left').run());
      button(t('editor.align-center', 'Center'), e => e.chain().focus().setTextAlign('center').run());
      button(t('editor.undo', 'Undo'), e => e.chain().focus().undo().run());
      button(t('editor.redo', 'Redo'), e => e.chain().focus().redo().run());
      textarea.hidden = true;
      if (!livePath) createEditor(false);
    } catch (error) {
      fail(error);
    }
  }
}

// Autosave (R62): a form with data-autosave posts name, labels, content and revision as the page's
// working copy a moment after the last change. Publishing (the form's own request) pauses it and
// drops a pending save, so a late autosave cannot bring back a working copy that was just published.
const DEBOUNCE_MS = 2000;
function autosave(form, textarea, editor) {
  const status = form.querySelector('[data-autosave-status]');
  const say = text => { if (status) status.textContent = text; };
  let timer = null, dirty = false, paused = false, stopped = false, inFlight = null;
  const csrf = () => {
    const token = document.querySelector('meta[name="_csrf"]');
    const header = document.querySelector('meta[name="_csrf_header"]');
    return token && header ? { [header.content]: token.content } : {};
  };
  const body = () => {
    const data = new URLSearchParams();
    for (const field of ['name', 'labels', 'revision']) {
      const input = form.elements[field];
      if (input) data.set(field, input.value);
    }
    data.set('content', clean(editor.getHTML()));
    data.set('live', textarea.knowledgerootLiveEditing ? 'true' : 'false');
    return data;
  };
  const save = async keepalive => {
    timer = null;
    if (paused || !dirty) return;
    dirty = false;
    if (inFlight) inFlight.abort();
    const controller = new AbortController();
    inFlight = controller;
    say(t('autosave.saving', 'Saving…'));
    try {
      const response = await fetch(form.dataset.autosave, {
        method: 'POST', body: body(), keepalive, signal: controller.signal,
        headers: { 'X-Requested-With': 'fetch', ...csrf() }
      });
      // 409 in a live session: the page was just published from the shared document and the new revision
      // arrives over the socket, which repeats the save.
      if (response.status === 409 && textarea.knowledgerootLiveEditing) { dirty = true; return; }
      if (!response.ok) throw new Error(String(response.status));
      const state = await response.json();
      const at = new Date(state.savedAt);
      say(t('autosave.saved', 'Saved at {0}', Number.isNaN(at.getTime()) ? state.savedAt : at.toLocaleTimeString()));
      form.dataset.autosaveStale = String(Boolean(state.stale));
    } catch (error) {
      if (error.name === 'AbortError') return;
      dirty = true;
      say(t('autosave.failed', 'Could not save the working copy. Your changes stay in this editor.'));
    } finally {
      if (inFlight === controller) inFlight = null;
    }
  };
  const changed = () => {
    dirty = true;
    if (paused) return;
    clearTimeout(timer);
    timer = setTimeout(save, DEBOUNCE_MS);
  };
  // Remote live changes arrive through the sync plugin; the browser that typed them saves them.
  editor.on('update', ({ transaction }) => { if (!transaction.getMeta(ySyncPluginKey)) changed(); });
  form.addEventListener('input', event => { if (event.target !== textarea && event.target.form === form) changed(); });
  form.addEventListener('htmx:beforeRequest', event => {
    if (event.detail.elt !== form) return;
    paused = true;
    clearTimeout(timer);
    timer = null;
    if (inFlight) inFlight.abort();
  });
  form.addEventListener('htmx:afterRequest', event => {
    if (event.detail.elt !== form || event.detail.successful || stopped) return;
    paused = false;
    if (dirty) changed();
  });
  textarea.knowledgerootAutosave = {
    flush: () => { clearTimeout(timer); return save(true); },
    revisionChanged: () => { if (dirty && !paused) changed(); },
    stop: () => { stopped = true; paused = true; clearTimeout(timer); timer = null; if (inFlight) inFlight.abort(); }
  };
}

// HTMX has already collected form values here: update its parameters as well as the textarea.
document.addEventListener('htmx:configRequest', event => {
  for (const [textarea, editor] of instances) {
    if (event.detail.elt === textarea.form) {
      textarea.value = clean(editor.getHTML());
      event.detail.parameters[textarea.name] = textarea.value;
      // Tells the server whether the content is the shared live document (R63).
      event.detail.parameters.live = textarea.knowledgerootLiveEditing ? 'true' : 'false';
    }
  }
});
document.addEventListener('submit', event => {
  for (const [textarea, editor] of instances) if (textarea.form === event.target) textarea.value = clean(editor.getHTML());
}, true);
document.addEventListener('htmx:beforeCleanupElement', event => {
  const removed = event.detail.elt;
  if (!removed || !removed.querySelectorAll) return;
  const fields = removed.matches('textarea[data-rich-editor]') ? [removed] : removed.querySelectorAll('textarea[data-rich-editor]');
  for (const textarea of fields) {
    if (textarea.knowledgerootAutosave) textarea.knowledgerootAutosave.flush();
    if (textarea.knowledgerootLive) textarea.knowledgerootLive.destroy();
    const editor = instances.get(textarea);
    if (editor) editor.destroy();
    instances.delete(textarea);
    delete textarea.knowledgerootEditor;
    delete textarea.knowledgerootLive;
    delete textarea.knowledgerootAutosave;
    delete textarea.knowledgerootLiveEditing;
    delete textarea.knowledgerootMounted;
  }
});
document.addEventListener('htmx:load', event => initialize(event.detail.elt));
if (document.readyState === 'loading') document.addEventListener('DOMContentLoaded', () => initialize(document));
else initialize(document);

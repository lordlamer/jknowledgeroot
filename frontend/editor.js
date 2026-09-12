import { Editor, Extension } from '@tiptap/core';
import StarterKit from '@tiptap/starter-kit';
import { TableKit } from '@tiptap/extension-table';
import Image from '@tiptap/extension-image';
import { TextStyleKit } from '@tiptap/extension-text-style';
import TextAlign from '@tiptap/extension-text-align';
import DOMPurify from 'dompurify';
import './editor.css';

const instances = new Map();
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

function initialize(root) {
  const fields = root.matches?.('textarea[data-rich-editor]') ? [root] : root.querySelectorAll('textarea[data-rich-editor]');
  for (const textarea of fields) {
    if (instances.has(textarea)) continue;
    const shell = document.createElement('div');
    shell.className = 'kr-rich-editor';
    const toolbar = document.createElement('div');
    toolbar.className = 'kr-rich-toolbar';
    toolbar.setAttribute('role', 'toolbar');
    toolbar.setAttribute('aria-label', 'Textformatierung');
    const surface = document.createElement('div');
    shell.append(toolbar, surface);
    textarea.before(shell);
    try {
      const buttons = [];
      const refresh = editor => buttons.forEach(({ button, active }) => {
        if (active) button.setAttribute('aria-pressed', String(active(editor)));
      });
      const editor = new Editor({
        element: surface,
        extensions: [
          StarterKit.configure({ link: { openOnClick: false, defaultProtocol: 'https', protocols: ['http', 'https', 'mailto'] } }),
          TableKit, Image.configure({ allowBase64: false }), TextStyleKit, ExistingBlockStyles,
          TextAlign.configure({ types: ['heading', 'paragraph'] })
        ],
        content: clean(textarea.value),
        editorProps: {
          attributes: { 'aria-label': 'Seiteninhalt', role: 'textbox', 'aria-multiline': 'true' },
          transformPastedHTML: clean
        },
        onUpdate: ({ editor }) => { textarea.value = editor.getHTML(); },
        onTransaction: ({ editor }) => refresh(editor)
      });
      const button = (label, action, active) => {
        const element = document.createElement('button');
        element.type = 'button';
        element.textContent = label;
        element.title = label;
        element.addEventListener('click', () => { action(editor); refresh(editor); });
        toolbar.append(element);
        buttons.push({ button: element, active });
      };
      const heading = document.createElement('select');
      heading.setAttribute('aria-label', 'Absatzformat');
      for (let level = 0; level <= 6; level++) heading.add(new Option(level ? `Überschrift ${level}` : 'Absatz', level));
      heading.addEventListener('change', () => {
        const level = Number(heading.value);
        if (level) editor.chain().focus().setHeading({ level }).run();
        else editor.chain().focus().setParagraph().run();
      });
      toolbar.append(heading);
      button('Fett', e => e.chain().focus().toggleBold().run(), e => e.isActive('bold'));
      button('Kursiv', e => e.chain().focus().toggleItalic().run(), e => e.isActive('italic'));
      button('Unterstrichen', e => e.chain().focus().toggleUnderline().run(), e => e.isActive('underline'));
      button('Durchgestrichen', e => e.chain().focus().toggleStrike().run(), e => e.isActive('strike'));
      button('Liste', e => e.chain().focus().toggleBulletList().run(), e => e.isActive('bulletList'));
      button('Nummerierung', e => e.chain().focus().toggleOrderedList().run(), e => e.isActive('orderedList'));
      button('Zitat', e => e.chain().focus().toggleBlockquote().run(), e => e.isActive('blockquote'));
      button('Codeblock', e => e.chain().focus().toggleCodeBlock().run(), e => e.isActive('codeBlock'));
      button('Link', e => {
        const url = window.prompt('Link-Adresse', e.getAttributes('link').href || 'https://');
        if (url === '') e.chain().focus().extendMarkRange('link').unsetLink().run();
        else if (url && safeUrl(url)) e.chain().focus().extendMarkRange('link').setLink({ href: url }).run();
      }, e => e.isActive('link'));
      button('Bild', e => {
        const url = window.prompt('Bild-Adresse (HTTPS oder relativer Pfad)');
        if (url && safeUrl(url) && !url.toLowerCase().startsWith('mailto:')) e.chain().focus().setImage({ src: url }).run();
      });
      button('Tabelle', e => e.chain().focus().insertTable({ rows: 3, cols: 3, withHeaderRow: true }).run());
      button('Zeile hinzufügen', e => e.chain().focus().addRowAfter().run());
      button('Spalte hinzufügen', e => e.chain().focus().addColumnAfter().run());
      button('Tabelle entfernen', e => e.chain().focus().deleteTable().run());
      button('Linksbündig', e => e.chain().focus().setTextAlign('left').run());
      button('Zentriert', e => e.chain().focus().setTextAlign('center').run());
      button('Rückgängig', e => e.chain().focus().undo().run());
      button('Wiederholen', e => e.chain().focus().redo().run());
      editor.on('selectionUpdate', () => {
        heading.value = String(editor.getAttributes('heading').level || 0);
      });
      textarea.hidden = true;
      textarea.knowledgerootEditor = editor;
      instances.set(textarea, editor);
      refresh(editor);
    } catch (error) {
      shell.remove();
      textarea.hidden = false;
      console.error('Rich text editor initialization failed', error);
    }
  }
}

// HTMX has already collected form values here: update its parameters as well as the textarea.
document.addEventListener('htmx:configRequest', event => {
  for (const [textarea, editor] of instances) {
    if (event.detail.elt === textarea.form) {
      textarea.value = clean(editor.getHTML());
      event.detail.parameters[textarea.name] = textarea.value;
    }
  }
});
document.addEventListener('submit', event => {
  for (const [textarea, editor] of instances) if (textarea.form === event.target) textarea.value = clean(editor.getHTML());
}, true);
document.addEventListener('htmx:beforeCleanupElement', event => {
  for (const [textarea, editor] of instances) {
    if (event.detail.elt === textarea || event.detail.elt.contains(textarea)) {
      editor.destroy();
      instances.delete(textarea);
      delete textarea.knowledgerootEditor;
    }
  }
});
document.addEventListener('htmx:load', event => initialize(event.detail.elt));
if (document.readyState === 'loading') document.addEventListener('DOMContentLoaded', () => initialize(document));
else initialize(document);

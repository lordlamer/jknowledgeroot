import { Editor, Extension } from '@tiptap/core';
import StarterKit from '@tiptap/starter-kit';
import { TableKit } from '@tiptap/extension-table';
import Image from '@tiptap/extension-image';
import { TextStyleKit } from '@tiptap/extension-text-style';
import TextAlign from '@tiptap/extension-text-align';
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

function initialize(root) {
  const fields = root.matches?.('textarea[data-rich-editor]') ? [root] : root.querySelectorAll('textarea[data-rich-editor]');
  for (const textarea of fields) {
    if (instances.has(textarea)) continue;
    const shell = document.createElement('div');
    shell.className = 'kr-rich-editor';
    const toolbar = document.createElement('div');
    toolbar.className = 'kr-rich-toolbar';
    toolbar.setAttribute('role', 'toolbar');
    toolbar.setAttribute('aria-label', t('editor.toolbar', 'Text formatting'));
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
          attributes: { 'aria-label': t('editor.content', 'Page content'), role: 'textbox', 'aria-multiline': 'true' },
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
      heading.setAttribute('aria-label', t('editor.block-format', 'Paragraph format'));
      for (let level = 0; level <= 6; level++) heading.add(new Option(level ? t('editor.heading', 'Heading {0}', level) : t('editor.paragraph', 'Paragraph'), level));
      heading.addEventListener('change', () => {
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

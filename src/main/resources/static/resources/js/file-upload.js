// Attachment upload dialog on the page view. Handlers are delegated so they survive HTMX content swaps.
(function () {
    'use strict';

    // Texts come from window.krT (page head); the English fallbacks keep the dialog usable without it.
    function t(key, fallback) {
        if (window.krT) return window.krT.apply(null, arguments);
        var text = fallback;
        for (var i = 2; i < arguments.length; i++) text = text.split('{' + (i - 2) + '}').join(String(arguments[i]));
        return text;
    }

    function message(status) {
        switch (status) {
            case 400: return t('upload.error.400', 'The upload was rejected. Check that no file is empty.');
            case 403: return t('upload.error.403', 'You do not have permission to upload files to this page.');
            case 413: return t('request.413', 'The upload exceeds the allowed size or file count.');
            case 503: return t('request.503', 'File storage is currently unavailable. Please try again later.');
            default: return t('upload.error.failed', 'The upload failed. Please try again.');
        }
    }

    function formatSize(bytes) {
        if (bytes < 1024) return bytes + ' B';
        if (bytes < 1048576) return (bytes / 1024).toFixed(1) + ' KB';
        return (bytes / 1048576).toFixed(1) + ' MB';
    }

    function dialog() { return document.getElementById('krUploadDialog'); }

    function limits(d) {
        return { files: parseInt(d.dataset.maxFiles, 10) || 10, bytes: parseInt(d.dataset.maxFileBytes, 10) || Infinity };
    }

    function problems(d) {
        var max = limits(d);
        var files = d._files || [];
        if (files.length > max.files) return t('upload.too-many', 'Choose at most {0} files.', max.files);
        var tooLarge = files.filter(function (f) { return f.size > max.bytes; });
        if (tooLarge.length) return t('upload.too-large', '{0} is larger than {1}.', tooLarge[0].name, formatSize(max.bytes));
        var empty = files.filter(function (f) { return f.size === 0; });
        if (empty.length) return t('upload.empty', '{0} is empty.', empty[0].name);
        return null;
    }

    function showError(d, message) {
        var el = d.querySelector('.kr-upload-error');
        el.textContent = message || '';
        el.hidden = !message;
    }

    function render(d) {
        var list = d.querySelector('.kr-upload-list');
        var max = limits(d);
        list.innerHTML = '';
        (d._files || []).forEach(function (file, index) {
            var li = document.createElement('li');
            if (file.size > max.bytes || file.size === 0) li.className = 'invalid';
            var name = document.createElement('span');
            name.className = 'kr-upload-name';
            name.textContent = file.name;
            var size = document.createElement('span');
            size.className = 'kr-upload-size';
            size.textContent = formatSize(file.size);
            var remove = document.createElement('button');
            remove.type = 'button';
            remove.className = 'kr-upload-remove';
            remove.setAttribute('aria-label', t('upload.remove', 'Remove {0}', file.name));
            remove.dataset.krUploadRemove = String(index);
            remove.innerHTML = '<i class="bi bi-x" aria-hidden="true"></i>';
            li.append(name, size, remove);
            list.appendChild(li);
        });
        var problem = problems(d);
        showError(d, problem);
        d.querySelector('[data-kr-upload-submit]').disabled = !d._files.length || !!problem || d._busy;
    }

    function addFiles(d, fileList) {
        d._files = (d._files || []).concat(Array.from(fileList || []));
        render(d);
    }

    function open(files) {
        var d = dialog();
        if (!d) return;
        d._files = [];
        d._busy = false;
        d.querySelector('.kr-upload-progress').hidden = true;
        d.querySelector('#krUploadInput').value = '';
        render(d);
        if (!d.open) d.showModal();
        if (files && files.length) addFiles(d, files);
    }

    function close(d) {
        if (d._busy) return;
        d.close();
    }

    function upload(d) {
        if (d._busy || !d._files.length || problems(d)) return;
        var pageId = d.dataset.pageId;
        var data = new FormData();
        data.append('pageId', pageId);
        d._files.forEach(function (file) { data.append('file', file, file.name); });

        var xhr = new XMLHttpRequest();
        xhr.open('POST', '/ui/file');
        var token = document.querySelector('meta[name="_csrf"]');
        var header = document.querySelector('meta[name="_csrf_header"]');
        if (token && header) xhr.setRequestHeader(header.content, token.content);

        var progress = d.querySelector('.kr-upload-progress');
        var bar = d.querySelector('.kr-upload-bar');
        var submit = d.querySelector('[data-kr-upload-submit]');
        d._busy = true;
        submit.disabled = true;
        submit.textContent = t('upload.uploading', 'Uploading…');
        progress.hidden = false;
        bar.style.width = '0%';
        showError(d, null);

        xhr.upload.addEventListener('progress', function (event) {
            if (event.lengthComputable) bar.style.width = Math.round(event.loaded / event.total * 100) + '%';
        });
        function finish(error) {
            d._busy = false;
            submit.textContent = t('upload.submit', 'Upload');
            if (error) {
                progress.hidden = true;
                showError(d, error);
                render(d);
                return;
            }
            d.close();
            // The server redirects to the page; reload it into the content area so the new attachments show up.
            if (window.htmx) htmx.ajax('GET', '/ui/page/' + pageId, { target: '#content', swap: 'innerHTML' });
            else window.location.reload();
        }
        xhr.addEventListener('load', function () {
            finish(xhr.status >= 200 && xhr.status < 400 ? null : message(xhr.status));
        });
        xhr.addEventListener('error', function () { finish(t('upload.error.network', 'The upload failed. Check your connection and try again.')); });
        xhr.send(data);
    }

    document.addEventListener('click', function (event) {
        var opener = event.target.closest('[data-kr-upload-open]');
        if (opener) { event.preventDefault(); open(); return; }
        var d = dialog();
        if (!d || !d.contains(event.target)) return;
        if (event.target === d) { close(d); return; } // backdrop click
        if (event.target.closest('[data-kr-upload-close]')) { event.preventDefault(); close(d); return; }
        if (event.target.closest('[data-kr-upload-submit]')) { event.preventDefault(); upload(d); return; }
        var remove = event.target.closest('[data-kr-upload-remove]');
        if (remove && !d._busy) {
            d._files.splice(parseInt(remove.dataset.krUploadRemove, 10), 1);
            render(d);
        }
    });

    document.addEventListener('change', function (event) {
        if (event.target.id !== 'krUploadInput') return;
        var d = dialog();
        if (d && !d._busy) addFiles(d, event.target.files);
        event.target.value = '';
    });

    document.addEventListener('cancel', function (event) {
        if (event.target === dialog() && dialog()._busy) event.preventDefault();
    }, true);

    // Drag and drop onto the dialog drop zone or the empty attachment area.
    function dropTarget(event) {
        return event.target.closest && event.target.closest('.kr-dropzone, .kr-attach-empty');
    }
    ['dragenter', 'dragover'].forEach(function (type) {
        document.addEventListener(type, function (event) {
            var target = dropTarget(event);
            if (!target || !event.dataTransfer || Array.from(event.dataTransfer.types).indexOf('Files') < 0) return;
            event.preventDefault();
            event.dataTransfer.dropEffect = 'copy';
            target.classList.add('dragover');
        });
    });
    document.addEventListener('dragleave', function (event) {
        var target = dropTarget(event);
        if (target && !target.contains(event.relatedTarget)) target.classList.remove('dragover');
    });
    document.addEventListener('drop', function (event) {
        var target = dropTarget(event);
        if (!target || !event.dataTransfer || !event.dataTransfer.files.length) return;
        event.preventDefault();
        target.classList.remove('dragover');
        var d = dialog();
        if (!d) return;
        if (target.classList.contains('kr-attach-empty') || !d.open) open(event.dataTransfer.files);
        else if (!d._busy) addFiles(d, event.dataTransfer.files);
    });
})();

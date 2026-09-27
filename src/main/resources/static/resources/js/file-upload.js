// Attachment upload dialog on the page view. Handlers are delegated so they survive HTMX content swaps.
(function () {
    'use strict';

    var MESSAGES = {
        400: 'The upload was rejected. Check that no file is empty.',
        403: 'You do not have permission to upload files to this page.',
        413: 'The upload exceeds the allowed size or file count.',
        503: 'File storage is currently unavailable. Please try again later.'
    };

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
        if (files.length > max.files) return 'Choose at most ' + max.files + ' files.';
        var tooLarge = files.filter(function (f) { return f.size > max.bytes; });
        if (tooLarge.length) return tooLarge[0].name + ' is larger than ' + formatSize(max.bytes) + '.';
        var empty = files.filter(function (f) { return f.size === 0; });
        if (empty.length) return empty[0].name + ' is empty.';
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
            remove.setAttribute('aria-label', 'Remove ' + file.name);
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
        submit.textContent = 'Uploading…';
        progress.hidden = false;
        bar.style.width = '0%';
        showError(d, null);

        xhr.upload.addEventListener('progress', function (event) {
            if (event.lengthComputable) bar.style.width = Math.round(event.loaded / event.total * 100) + '%';
        });
        function finish(error) {
            d._busy = false;
            submit.textContent = 'Upload';
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
            finish(xhr.status >= 200 && xhr.status < 400 ? null : (MESSAGES[xhr.status] || 'The upload failed. Please try again.'));
        });
        xhr.addEventListener('error', function () { finish('The upload failed. Check your connection and try again.'); });
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

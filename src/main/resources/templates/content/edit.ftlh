<form hx-post="/ui/page/${page.id}/content/${content.contentId}/edit" hx-target="#content">
    <div class="card" style="margin-bottom: 20px;">
        <div class="card-header">
            <ul class="nav nav-tabs card-header-tabs">
                <li class="nav-item">
                    <a class="nav-link active" aria-current="true" href="#">Content</a>
                </li>
                <li class="nav-item">
                    <a class="nav-link" href="#">Permissions</a>
                </li>
            </ul>
        </div>
        <div class="card-body">
            <p class="card-text">
                <div class="mb-3">
                    <label for="page-name" class="form-label">Name</label>
                    <input type="text" class="form-control" id="page-name" name="name" value="${content.name}">
                </div>
                <div class="mb-3">
                    <label for="page-subtitle" class="form-label">Content</label>
                    <textarea class="form-control" id="page-subtitle" rows="3" name="content">${content.content}</textarea>
                </div>
            </p>
        </div>
    </div>
    <div>
        <button type="submit" class="btn btn-success">save</button>
        <button type="button" class="btn btn-outline-secondary">cancel</button>
    </div>
</form>
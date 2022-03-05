<nav aria-label="breadcrumb">
    <ol class="breadcrumb">
        <li class="breadcrumb-item"><a href="#">Home</a></li>
        <li class="breadcrumb-item"><a href="#">Library</a></li>
        <li class="breadcrumb-item active" aria-current="page">Data</li>
    </ol>
</nav>

<div class="btn-group btn-group-sm" role="group" aria-label="Basic example">
    <button type="button" class="btn btn-outline-secondary">add new page</button>
    <button type="button" class="btn btn-outline-secondary">edit page</button>
    <button type="button" class="btn btn-outline-secondary">move page</button>
    <button type="button" class="btn btn-outline-secondary">delete page</button>
    <button type="button" class="btn btn-outline-secondary">add new content</button>
</div>

<h1>${page.name}</h1>

<p>
    ${page.subtitle}
</p>

<#list contents as content>
<div class="card" style="margin-bottom: 20px;">
    <div class="card-header">
        <a data-bs-toggle="collapse" href="#content-${content.id}" aria-expanded="true" aria-controls="content-${content.id}" id="content-head-${content.id}" class="d-block ">
            <i class="bi bi-chevron-down float-end"></i>
            ${content.name}
        </a>
    </div>
    <div class="collapse show" id="content-${content.id}">
        <div class="card-body">
            <div class="btn-group btn-group-sm" role="group" aria-label="Basic example">
                <button type="button" class="btn btn-outline-secondary">edit</button>
                <button type="button" class="btn btn-outline-secondary">delete</button>
                <button type="button" class="btn btn-outline-secondary">move</button>
                <button type="button" class="btn btn-outline-secondary">print</button>
                <button type="button" class="btn btn-outline-secondary">add file</button>
            </div>
            <p class="card-text">
                ${content.content}
            </p>

            <ul class="list-group">
                <li class="list-group-item list-group-item-light">
                    <a href="javascript:;"><i class="fa fa-trash" aria-hidden="true"></i></a>&nbsp;
                    <a href="javascript:;" title="admin - 26. Jan 2009 19:17:54"><i class="fa fa-cloud-download" aria-hidden="true"></i>file1</a>
                    [578.42 KB] [admin - 26. Jan 2009 19:17:54]
                </li>
                <li class="list-group-item list-group-item-light">
                    <a href="javascript:;"><i class="fa fa-trash" aria-hidden="true"></i></a>&nbsp;
                    <a href="javascript:;" title="admin - 26. Jan 2009 19:17:54"><i class="fa fa-cloud-download" aria-hidden="true"></i>file1</a>
                    [578.42 KB] [admin - 26. Jan 2009 19:17:54]
                </li>
            </ul>
        </div>
        <div class="card-footer text-muted">
            Last modified by ${content.changedBy} on ${content.changeDate} | created: ${content.createDate} by ${content.createdBy}
        </div>
    </div>
</div>
</#list>
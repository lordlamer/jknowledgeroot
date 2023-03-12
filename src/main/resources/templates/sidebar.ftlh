<#if parentId == 0>
<ul class="list-group list-group-flush">
    <#list pages as page>
        <li class="list-group-item list-group-item-action" id="nav-page-${page.id}">
                <a href="javascript:;" hx-get="/ui/sidebar?parent=${page.id}" hx-target="#nav-page-${page.id}" hx-swap="outerHTML"><i class="bi bi-chevron-right"></i></a>
                <a href="javascript:;" hx-get="/ui/page/${page.id}" hx-target="#content" hx-push-url="true">${page.name}</a>
        </li>
    </#list>
</ul>
<#else>
    <li class="list-group-item list-group-item-action" id="nav-page-${parentPage.id}">
        <a href="javascript:;"><i class="bi bi-chevron-down"></i></a>
        <a href="javascript:;" hx-get="/ui/page/${parentPage.id}" hx-target="#content" hx-push-url="true">${parentPage.name}</a>
    </li>
    <#list pages as page>
        <li class="list-group-item list-group-item-action" id="nav-page-${page.id}">
            <a href="javascript:;" hx-get="/ui/sidebar?parent=${page.id}" hx-target="#nav-page-${page.id}" hx-swap="outerHTML"><i class="bi bi-chevron-right"></i></a>
            <a href="javascript:;" hx-get="/ui/page/${page.id}" hx-target="#content" hx-push-url="true">${page.name}</a>
        </li>
    </#list>
</#if>

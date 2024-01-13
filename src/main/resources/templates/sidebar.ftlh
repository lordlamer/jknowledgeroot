<#if parentId == 0>
    <#if singlePage??>
        <li class="list-group-item" style="border:0;" id="nav-page-${singlePage.pageId}">
            <a href="javascript:;" hx-get="/ui/sidebar?parent=${singlePage.pageId}" hx-target="#nav-page-${singlePage.pageId}" hx-swap="outerHTML"><span class="tree-indicator"></span><i class="bi bi-chevron-right"></i></a>
            <a href="javascript:;" hx-get="/ui/page/${singlePage.pageId}" hx-target="#content" hx-push-url="true">${singlePage.name}</a>
        </li>
    <#else>
        <ul class="list-group">
            <#list pages as page>
            <li class="list-group-item" style="border:0;" id="nav-page-${page.pageId}">
                <a href="javascript:;" hx-get="/ui/sidebar?parent=${page.pageId}" hx-target="#nav-page-${page.pageId}" hx-swap="outerHTML"><span class="tree-indicator"></span><i class="bi bi-chevron-right"></i></a>
                <a href="javascript:;" hx-get="/ui/page/${page.pageId}" hx-target="#content" hx-push-url="true">${page.name}</a>
            </li>
            </#list>
        </ul>
    </#if>
<#else>
    <li class="list-group-item" style="border:0;" id="nav-page-${parentPage.pageId}">
        <#if parentPage.parent == 0>
            <a href="javascript:;" hx-get="/ui/sidebar?singlePage=${parentPage.pageId}" hx-target="#nav-page-${parentPage.pageId}" hx-swap="outerHTML">
                <span class="tree-indicator"></span><i class="bi bi-chevron-down"></i>
            </a>
        <#else>
            <a href="javascript:;" hx-get="/ui/sidebar?parent=${parentPage.parent}" hx-target="#nav-page-${parentPage.parent}" hx-swap="outerHTML">
                <span class="tree-indicator"></span><i class="bi bi-chevron-down"></i>
            </a>
        </#if>
        <a href="javascript:;" hx-get="/ui/page/${parentPage.pageId}" hx-target="#content" hx-push-url="true">
            ${parentPage.name}
        </a>
        <ul class="tree-node" style="padding-left:0px;">
            <#list pages as page>
                <li class="list-group-item" style="border:0;" id="nav-page-${page.pageId}">
                    <a href="javascript:;" hx-get="/ui/sidebar?parent=${page.pageId}" hx-target="#nav-page-${page.pageId}" hx-swap="outerHTML"><i class="bi bi-chevron-right"></i></a>
                    <a href="javascript:;" hx-get="/ui/page/${page.pageId}" hx-target="#content" hx-push-url="true">${page.name}</a>
                </li>
            </#list>
        </ul>
    </li>
</#if>

<ul class="list-unstyled ps-0">
    <#list pages as page>
        <li class="mb-1">
            <a href="javascript:;" hx-get="/page/${page.id}" hx-target="#content">${page.name}</a>
        </li>
    </#list>
</ul>
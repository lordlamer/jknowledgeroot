export const TheKnowledgerootBreadcrumb = {
    data() {
        return {
            name: "FooBar"
        }
    },
    template: `
        <nav aria-label="breadcrumb">
            <ol class="breadcrumb">
                <li class="breadcrumb-item"><i class="fa fa-home fa-lg"></i> <a href="#">Home</a></li>
                <li class="breadcrumb-item"><a href="#">Library</a></li>
                <li class="breadcrumb-item active" aria-current="page">Data</li>
            </ol>
        </nav>`
}

export default TheKnowledgerootBreadcrumb;
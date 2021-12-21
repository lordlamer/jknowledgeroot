export const KnowledgerootContent = {
    props: ['title'],
    data() {
        return {
            content: "Hello World!"
        }
    },
    template: `
<div class="card" style="margin-bottom: 10px;">
  <div class="card-header">
    {{title}}
        <a class="text-end">
            <i class="fa fa-chevron-down pull-right"></i>    
        </a>
  </div>
  <div class="card-body">
    {{content}}
    <slot></slot>
  </div>
  <div class="card-footer text-muted">
    Last modified by guest on  | created: 
  </div>
</div>
`
}

export default KnowledgerootContent;
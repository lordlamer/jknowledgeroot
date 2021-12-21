export const KnowledgerootPage = {
    data() {
        return {
            name: "PAGE:"
        }
    },
    template: `
    <div>
    {{ name }}
    <knowledgeroot-content title="FOO">BAR</knowledgeroot-content>
    <knowledgeroot-content></knowledgeroot-content>
    <knowledgeroot-content></knowledgeroot-content>
    </div>`
}

export default KnowledgerootPage;
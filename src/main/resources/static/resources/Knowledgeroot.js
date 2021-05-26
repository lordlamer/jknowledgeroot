import KnowledgerootContent from "./components/KnowledgerootContent.js"
import KnowledgerootFile from "./components/KnowledgerootFile.js"
import KnowledgerootPage from "./components/KnowledgerootPage.js"
import TheKnowledgerootSidebar from "./components/TheKnowledgerootSidebar.js"
import TheKnowledgerootNavbarTop from "./components/TheKnowledgerootNavbarTop.js"
import TheKnowledgerootNavbarBottom from "./components/TheKnowledgerootNavbarBottom.js"
import TheKnowledgerootBreadcrumb from "./components/TheKnowledgerootBreadcrumb.js"

const KnowledgerootApp = Vue.createApp({})

KnowledgerootApp.component('knowledgeroot-content', KnowledgerootContent)
KnowledgerootApp.component('knowledgeroot-file', KnowledgerootFile)
KnowledgerootApp.component('knowledgeroot-page', KnowledgerootPage)
KnowledgerootApp.component('knowledgeroot-sidebar', TheKnowledgerootSidebar)
KnowledgerootApp.component('knowledgeroot-navbar-top', TheKnowledgerootNavbarTop)
KnowledgerootApp.component('knowledgeroot-navbar-bottom', TheKnowledgerootNavbarBottom)
KnowledgerootApp.component('knowledgeroot-breadcrumb', TheKnowledgerootBreadcrumb)

KnowledgerootApp.mount('#knowledgeroot-app')
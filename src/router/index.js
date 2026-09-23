/**
 * 라우터.
 *
 * GitHub Pages는 정적 호스팅이라 서버에 "모든 경로를 index.html로 돌려보내는"
 * rewrite 규칙이 없다. history 모드(경로에 #이 없는 방식)를 쓰면 새로고침하거나
 * 주소를 직접 입력했을 때 404가 뜬다. hash 모드(#/preview 처럼 #이 붙는 방식)는
 * 서버가 항상 index.html 하나만 돌려주면 되므로, 별도 서버 설정 없이 정적
 * 호스팅에서도 항상 정상 동작한다.
 */
import { createRouter, createWebHashHistory } from 'vue-router'
import ProductionDashboardPage from '../components/pages/ProductionDashboardPage.vue'
import DocumentPreviewPage from '../components/pages/DocumentPreviewPage.vue'
import DocumentLibraryPage from '../components/pages/DocumentLibraryPage.vue'

const router = createRouter({
  history: createWebHashHistory(),
  routes: [
    { path: '/', name: 'dashboard', component: ProductionDashboardPage },
    { path: '/preview', name: 'document-preview', component: DocumentPreviewPage },
    { path: '/documents', name: 'document-library', component: DocumentLibraryPage },
  ],
})

export default router

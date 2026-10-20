<script setup lang="ts">
const columns = [
  { title: '产品', links: ['智能问答', '知识库浏览', '文档上传', '私有化部署'] },
  { title: '技术', links: ['RAG 架构', 'Embedding 模型', '爬虫策略', 'GitHub'] },
  { title: '支持', links: ['使用指南', 'API 文档', '问题反馈', '联系我们'] },
]
</script>

<template>
  <footer class="footer">
    <div class="container">
      <div class="footer__grid">
        <div>
          <div class="footer__brand">
            <span class="footer__brand-icon">
              <svg width="16" height="16" viewBox="0 0 24 24">
                <path d="M4 4h16v4H4V4zm0 8h10v8H4v-8zm12 0h4v8h-4v-8z" fill="#fff" />
              </svg>
            </span>
            <span class="footer__brand-name">SpringKB</span>
          </div>
          <p class="footer__brand-desc">
            面向开发者的框架文档 RAG 搜索引擎。整合 Spring 全家桶、Vue 等主流框架官方文档，提供自然语言问答与精准溯源。
          </p>
        </div>

        <div v-for="col in columns" :key="col.title">
          <div class="footer__col-title">{{ col.title }}</div>
          <ul class="footer__links">
            <li v-for="link in col.links" :key="link" class="footer__link">
              <a href="#">{{ link }}</a>
            </li>
          </ul>
        </div>
      </div>

      <div class="footer__bottom">
        <span>© 2026 SpringKB. 基于 Spring Boot + Spring AI + pgvector 构建</span>
        <span>v2.1</span>
      </div>
    </div>
  </footer>
</template>

<style scoped>
.footer {
  margin-top: auto;
  padding: var(--sp-12) 0 var(--sp-8);
  background: var(--ink-900);
  color: var(--ink-300);
}

.footer__grid {
  display: grid;
  grid-template-columns: 2fr 1fr 1fr 1fr;
  gap: var(--sp-10);
  margin-bottom: var(--sp-10);
}

.footer__brand {
  display: flex;
  align-items: center;
  gap: var(--sp-2);
  margin-bottom: var(--sp-3);
}

.footer__brand-icon {
  width: 28px;
  height: 28px;
  border-radius: var(--r-md);
  background: rgba(255, 255, 255, 0.1);
  display: grid;
  place-items: center;
}

.footer__brand-name {
  color: #fff;
  font-weight: 600;
  font-size: var(--fs-xl);
}

.footer__brand-desc {
  font-size: var(--fs-sm);
  line-height: 1.7;
  color: rgba(255, 255, 255, 0.62);
  margin-top: var(--sp-3);
  max-width: 320px;
}

.footer__col-title {
  font-size: var(--fs-sm);
  font-weight: 600;
  color: var(--ink-100);
  text-transform: uppercase;
  letter-spacing: 0.05em;
  margin-bottom: var(--sp-4);
}

.footer__links {
  list-style: none;
}

.footer__link {
  font-size: var(--fs-sm);
  color: rgba(255, 255, 255, 0.62);
  padding: var(--sp-2) 0;
  transition: color var(--dur-fast) var(--ease-out);
}

.footer__link:hover {
  color: #fff;
}

.footer__bottom {
  border-top: 1px solid rgba(255, 255, 255, 0.12);
  padding-top: var(--sp-6);
  display: flex;
  justify-content: space-between;
  align-items: center;
  font-size: var(--fs-sm);
  color: rgba(255, 255, 255, 0.62);
}

@media (max-width: 1024px) {
  .footer__grid {
    grid-template-columns: repeat(2, 1fr);
  }
}

@media (max-width: 640px) {
  .footer__grid {
    grid-template-columns: 1fr;
    gap: var(--sp-8);
  }
}
</style>

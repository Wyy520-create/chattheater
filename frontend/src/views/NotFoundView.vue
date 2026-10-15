<template>
  <div class="nf page">
    <div class="nf-code">404</div>
    <h2>这场戏不在排片表上</h2>
    <p>页面不存在，或剧目已下映。</p>
    <div class="nf-act">
      <router-link to="/" class="btn btn-primary">回首页</router-link>
      <router-link to="/plaza" class="btn btn-ghost">去广场逛逛</router-link>
    </div>
  </div>
</template>

<style scoped>
.nf {
  text-align: center;
  padding-top: 110px;
}

.nf-code {
  font-size: 88px;
  font-weight: 900;
  font-family: var(--font-mono);
  background: linear-gradient(120deg, #ffe08a, #f5c518 45%, #ff9f43);
  -webkit-background-clip: text;
  background-clip: text;
  color: transparent;
  line-height: 1;
}

.nf h2 {
  margin-top: 18px;
  font-size: 22px;
}

.nf p {
  color: var(--text-3);
  margin-top: 8px;
}

.nf-act {
  margin-top: 28px;
  display: flex;
  gap: 12px;
  justify-content: center;
}
</style>

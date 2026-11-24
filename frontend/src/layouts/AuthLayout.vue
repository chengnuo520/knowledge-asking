<template>
  <div class="auth-shell">
    <div class="auth-brand">
      <div class="logo">KB</div>
      <span>知识库问答平台</span>
    </div>

    <div class="auth-card">
      <router-view />
    </div>

    <div class="auth-foot">AgentScope · Spring Boot 3 · Vue 3</div>
  </div>
</template>

<style scoped>
.auth-shell {
  height: 100%;
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  gap: 24px;
  padding: 32px 16px;
  box-sizing: border-box;
  background: #f5f7fa;
}

.auth-brand {
  display: flex;
  align-items: center;
  gap: 10px;
  font-size: 15px;
  font-weight: 600;
  color: #1d2129;
}

.logo {
  width: 34px;
  height: 34px;
  border-radius: 10px;
  background: linear-gradient(135deg, #4f46e5, #7c3aed);
  color: #fff;
  font-size: 13px;
  font-weight: 700;
  display: flex;
  align-items: center;
  justify-content: center;
}

.auth-card {
  width: 100%;
  max-width: 420px;
  padding: 40px 40px 32px;
  box-sizing: border-box;
  background: #fff;
  border-radius: 16px;
  box-shadow: 0 8px 32px rgba(29, 33, 41, 0.08);
  display: flex;
  justify-content: center;
}

.auth-foot { font-size: 12px; color: #c9cdd4; }

@media (max-width: 520px) {
  .auth-card { padding: 28px 22px 24px; }
}
</style>

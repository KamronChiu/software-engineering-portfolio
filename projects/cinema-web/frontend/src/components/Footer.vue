<template>
  <footer class="global-footer">
    © {{ new Date().getFullYear() }} Kamron版权所有
  </footer>
</template>

<script>
export default {
  name: 'GlobalFooter'
}
</script>

<style scoped>
.global-footer {
  text-align: center;
  padding: 12px 0;
  font-size: 14px;
  color: #aaa;
  background-color: #1e1f23;
  border-top: 1px solid rgba(255,255,255,0.1);
}
</style>
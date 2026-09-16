<template>
  <AdminLayout>
    <div class="system-settings">
      <div class="settings-card">
        <div class="card-header">
          <el-icon :size="22"><Lock /></el-icon>
          <h3>专注模式密码</h3>
        </div>
        <p class="card-desc">设置或修改学生进入专注模式所需的密码。密码至少4位，修改后立即生效。</p>

        <el-form :model="form" label-width="100px" class="settings-form">
          <el-form-item label="新密码">
            <el-input v-model="form.password" type="password" show-password placeholder="请输入新密码（至少4位）" maxlength="20" />
          </el-form-item>
          <el-form-item label="确认密码">
            <el-input v-model="form.confirm" type="password" show-password placeholder="请再次输入新密码" maxlength="20" @keyup.enter="handleSave" />
          </el-form-item>
          <el-form-item>
            <el-button type="primary" :loading="saving" @click="handleSave">保存修改</el-button>
          </el-form-item>
        </el-form>
      </div>

      <div class="settings-card">
        <div class="card-header">
          <el-icon :size="22"><InfoFilled /></el-icon>
          <h3>专注模式说明</h3>
        </div>
        <ul class="desc-list">
          <li>学生进入专注模式需要输入此处设置的密码</li>
          <li>退出专注模式同样需要输入密码</li>
          <li>专注模式下，30秒无操作将自动退出登录（不退出专注模式）</li>
          <li>密码存储在服务端，所有设备共享同一密码</li>
          <li>若未设置密码，首位使用者可自行设置初始密码</li>
        </ul>
      </div>
    </div>
  </AdminLayout>
</template>

<script setup>
import { reactive, ref } from 'vue'
import AdminLayout from '@/components/AdminLayout.vue'
import { ElMessage } from 'element-plus'
import { adminSetFocusPassword } from '@/api'
import { Lock, InfoFilled } from '@element-plus/icons-vue'

const form = reactive({ password: '', confirm: '' })
const saving = ref(false)

async function handleSave() {
  if (!form.password || form.password.length < 4) {
    ElMessage.warning('密码至少4位')
    return
  }
  if (form.password !== form.confirm) {
    ElMessage.warning('两次输入的密码不一致')
    return
  }
  saving.value = true
  try {
    await adminSetFocusPassword(form.password)
    ElMessage.success('专注模式密码修改成功')
    form.password = ''
    form.confirm = ''
  } catch (e) {
    ElMessage.error(e?.response?.data?.message || '修改失败，请稍后重试')
  } finally {
    saving.value = false
  }
}
</script>

<style scoped>
.system-settings {
  display: flex;
  flex-direction: column;
  gap: 20px;
  max-width: 700px;
}

.settings-card {
  background: #fff;
  border-radius: 12px;
  padding: 24px;
  box-shadow: 0 2px 12px rgba(0, 0, 0, 0.06);
}

.card-header {
  display: flex;
  align-items: center;
  gap: 10px;
  margin-bottom: 8px;
  color: #303133;
}

.card-header h3 {
  margin: 0;
  font-size: 18px;
}

.card-desc {
  color: #909399;
  font-size: 13px;
  margin: 0 0 20px 0;
}

.settings-form {
  max-width: 450px;
}

.desc-list {
  margin: 0;
  padding-left: 20px;
  color: #606266;
  font-size: 14px;
  line-height: 2;
}

.desc-list li {
  margin-bottom: 4px;
}
</style>

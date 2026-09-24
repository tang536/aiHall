<template>
  <div class="feedback-page">
    <div class="page-header">
      <h2><el-icon><ChatDotSquare /></el-icon> 意见反馈</h2>
      <p>你的建议与问题会直接送达平台管理员，处理结果会在这里回复给你</p>
    </div>

    <div class="feedback-main">
      <!-- 提交反馈 -->
      <div class="card">
        <h3 class="card-title">我要反馈</h3>
        <el-form :model="form" label-width="80px" :rules="rules" ref="formRef">
          <el-form-item label="反馈类型" prop="type">
            <el-radio-group v-model="form.type">
              <el-radio v-for="t in FEEDBACK_TYPES" :key="t.value" :value="t.value">{{ t.label }}</el-radio>
            </el-radio-group>
          </el-form-item>
          <el-form-item label="标题" prop="title">
            <el-input v-model="form.title" maxlength="100" show-word-limit placeholder="一句话概括你的反馈" />
          </el-form-item>
          <el-form-item label="详细描述" prop="content">
            <el-input
              v-model="form.content"
              type="textarea"
              :rows="5"
              maxlength="2000"
              show-word-limit
              placeholder="请描述具体情况、复现步骤或你的建议"
            />
          </el-form-item>
          <el-form-item label="截图">
            <ImageUpload v-model="form.images" :max="3" />
          </el-form-item>
          <el-form-item label="联系方式">
            <el-input v-model="form.contact" maxlength="100" placeholder="选填：手机号 / 邮箱 / QQ，仅管理员可见" />
          </el-form-item>
          <el-form-item>
            <el-button type="primary" :loading="submitting" @click="submit">
              <el-icon><Promotion /></el-icon> 提交反馈
            </el-button>
            <el-button @click="resetForm">重置</el-button>
          </el-form-item>
        </el-form>
      </div>

      <!-- 我的反馈 -->
      <div class="card">
        <div class="card-head">
          <h3 class="card-title">我的反馈记录</h3>
          <el-button size="small" text @click="loadMine">
            <el-icon><Refresh /></el-icon> 刷新
          </el-button>
        </div>

        <div v-loading="loading" class="fb-list">
          <div v-for="fb in list" :key="fb.id" class="fb-item">
            <div class="fb-head">
              <span class="fb-type">{{ FEEDBACK_TYPE_LABEL[fb.type] || '其他' }}</span>
              <span class="fb-title">{{ fb.title || '(未填标题)' }}</span>
              <el-tag size="small" :type="FEEDBACK_STATUS_TYPE[fb.status]">
                {{ FEEDBACK_STATUS_LABEL[fb.status] || fb.status }}
              </el-tag>
            </div>
            <p class="fb-content">{{ fb.content }}</p>
            <div v-if="splitImages(fb.images).length" class="fb-images">
              <el-image
                v-for="(url, idx) in splitImages(fb.images)"
                :key="idx"
                :src="url"
                fit="cover"
                class="fb-image"
                :preview-src-list="splitImages(fb.images)"
                :initial-index="idx"
              />
            </div>
            <div class="fb-foot">
              <span>{{ relativeTime(fb.createTime) }}</span>
              <el-button size="small" text type="danger" @click="doDelete(fb)">删除</el-button>
            </div>

            <!-- 管理员回复 -->
            <div v-if="fb.adminReply" class="admin-reply">
              <div class="admin-reply-head">
                <el-icon><Comment /></el-icon> 管理员回复
                <span class="reply-time">{{ relativeTime(fb.replyTime) }}</span>
              </div>
              <p class="admin-reply-text">{{ fb.adminReply }}</p>
            </div>
          </div>
          <el-empty v-if="!loading && list.length === 0" description="还没有提交过反馈" :image-size="70" />
        </div>
      </div>
    </div>
  </div>
</template>

<script setup>
import { onMounted, reactive, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import ImageUpload from '@/components/ImageUpload.vue'
import { submitFeedback, myFeedback, deleteFeedback } from '@/api'
import {
  FEEDBACK_TYPES, FEEDBACK_TYPE_LABEL, FEEDBACK_STATUS_LABEL, FEEDBACK_STATUS_TYPE,
  splitImages, relativeTime
} from '@/utils/community'

const formRef = ref(null)
const submitting = ref(false)
const loading = ref(false)
const list = ref([])

const form = reactive({ type: 'SUGGESTION', title: '', content: '', images: '', contact: '' })
const rules = {
  title: [{ required: true, message: '请填写反馈标题', trigger: 'blur' }],
  content: [{ required: true, message: '请填写详细描述', trigger: 'blur' }]
}

function resetForm() {
  Object.assign(form, { type: 'SUGGESTION', title: '', content: '', images: '', contact: '' })
}

async function submit() {
  try {
    await formRef.value.validate()
  } catch {
    return
  }
  submitting.value = true
  try {
    const res = await submitFeedback({ ...form })
    ElMessage.success(res?.message || '反馈已提交')
    resetForm()
    await loadMine()
  } finally {
    submitting.value = false
  }
}

async function loadMine() {
  loading.value = true
  try {
    const res = await myFeedback()
    list.value = res?.data || []
  } finally {
    loading.value = false
  }
}

async function doDelete(fb) {
  try {
    await ElMessageBox.confirm('确定删除这条反馈吗？', '删除确认', {
      confirmButtonText: '确定删除', cancelButtonText: '取消', type: 'warning'
    })
  } catch {
    return
  }
  await deleteFeedback(fb.id)
  ElMessage.success('已删除')
  await loadMine()
}

onMounted(loadMine)
</script>

<style scoped>
.feedback-main {
  display: grid;
  grid-template-columns: minmax(0, 1fr) minmax(0, 1fr);
  gap: 20px;
  align-items: start;
}

.card {
  background: var(--ct-white);
  border: 1px solid var(--ct-line);
  border-radius: 0;
  padding: 20px;
}

.card-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
}

.card-title {
  margin: 0 0 16px;
  font-size: 16px;
  color: var(--ct-ink);
}

.fb-list {
  display: grid;
  gap: 14px;
  min-height: 80px;
  max-height: 70vh;
  overflow-y: auto;
}

.fb-item {
  border: 1px solid var(--ct-line);
  border-radius: 0;
  padding: 14px;
  background: #faf7f2;
}

.fb-head {
  display: flex;
  align-items: center;
  gap: 8px;
  flex-wrap: wrap;
}

.fb-type {
  font-size: 12px;
  color: var(--ct-red);
  background: var(--ct-red-soft);
  border-radius: 0;
  padding: 1px 7px;
}

.fb-title {
  font-weight: 600;
  font-size: 14px;
  color: var(--ct-ink);
  flex: 1;
  min-width: 0;
}

.fb-content {
  margin: 8px 0 0;
  font-size: 13px;
  color: #4a4845;
  line-height: 1.75;
  white-space: pre-wrap;
}

.fb-images {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
  margin-top: 10px;
}

.fb-image {
  width: 84px;
  height: 84px;
  border-radius: 0;
  overflow: hidden;
}

.fb-foot {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-top: 10px;
  font-size: 12px;
  color: #a8a49c;
}

.admin-reply {
  margin-top: 12px;
  padding: 10px 12px;
  border-radius: 0;
  background: #e8f0ec;
  border-left: 3px solid var(--ct-green);
}

.admin-reply-head {
  display: flex;
  align-items: center;
  gap: 6px;
  font-size: 12px;
  color: var(--ct-green);
  font-weight: 600;
}

.reply-time {
  color: #8fb3a3;
  font-weight: 400;
}

.admin-reply-text {
  margin: 6px 0 0;
  font-size: 13px;
  color: var(--ct-green);
  line-height: 1.7;
  white-space: pre-wrap;
}

@media (max-width: 900px) {
  .feedback-main {
    grid-template-columns: 1fr;
  }
}
</style>

<template>
  <div class="image-upload">
    <div class="thumb-list">
      <div v-for="(url, idx) in images" :key="url + idx" class="thumb">
        <el-image :src="url" fit="cover" :preview-src-list="images" :initial-index="idx" class="thumb-img" />
        <div class="thumb-remove" @click="removeAt(idx)">
          <el-icon><Close /></el-icon>
        </div>
      </div>

      <el-upload
        v-if="images.length < max"
        class="uploader"
        :show-file-list="false"
        accept="image/*"
        :http-request="doUpload"
        :disabled="uploading"
      >
        <div class="upload-trigger" :class="{ disabled: uploading }">
          <el-icon v-if="!uploading" :size="22"><Plus /></el-icon>
          <el-icon v-else :size="22" class="is-loading"><Loading /></el-icon>
          <span>{{ uploading ? '上传中' : '添加图片' }}</span>
        </div>
      </el-upload>
    </div>
    <p class="upload-hint">
      最多 {{ max }} 张，单张不超过 5MB（支持 jpg/png/gif/webp/bmp），当前 {{ images.length }} 张
    </p>
  </div>
</template>

<script setup>
import { computed, ref } from 'vue'
import { ElMessage } from 'element-plus'
import { uploadImage } from '@/api'

const props = defineProps({
  /** 逗号分隔的图片地址串，与后端存储格式一致 */
  modelValue: { type: String, default: '' },
  max: { type: Number, default: 5 }
})
const emit = defineEmits(['update:modelValue'])

const uploading = ref(false)

const images = computed(() =>
  (props.modelValue || '')
    .split(',')
    .map((s) => s.trim())
    .filter(Boolean)
)

function persist(list) {
  emit('update:modelValue', list.join(','))
}

async function doUpload(option) {
  const file = option.file
  if (!file) return
  if (file.size > 5 * 1024 * 1024) {
    ElMessage.error('图片大小不能超过 5MB')
    return
  }
  uploading.value = true
  try {
    const res = await uploadImage(file)
    const url = res?.data?.url
    if (!url) {
      ElMessage.error('上传失败，请重试')
      return
    }
    persist([...images.value, url])
    ElMessage.success('图片已上传')
  } catch (e) {
    // 统一错误提示由 request 拦截器处理
  } finally {
    uploading.value = false
  }
}

function removeAt(idx) {
  const list = [...images.value]
  list.splice(idx, 1)
  persist(list)
}
</script>

<style scoped>
.image-upload {
  width: 100%;
}

.thumb-list {
  display: flex;
  flex-wrap: wrap;
  gap: 10px;
}

.thumb {
  position: relative;
  width: 84px;
  height: 84px;
  border-radius: 0;
  overflow: hidden;
  border: 1px solid var(--ct-line);
}

.thumb-img {
  width: 100%;
  height: 100%;
  display: block;
}

.thumb-remove {
  position: absolute;
  top: 0;
  right: 0;
  width: 20px;
  height: 20px;
  display: flex;
  align-items: center;
  justify-content: center;
  background: rgba(0, 0, 0, 0.5);
  color: #fff;
  font-size: 12px;
  cursor: pointer;
}

.upload-trigger {
  width: 84px;
  height: 84px;
  border: 1px dashed var(--ct-line);
  border-radius: 0;
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  gap: 4px;
  color: var(--ct-muted);
  font-size: 12px;
  cursor: pointer;
  transition: border-color 0.2s, color 0.2s;
  background: #faf7f2;
}

.upload-trigger:hover {
  border-color: var(--ct-red);
  color: var(--ct-red);
}

.upload-trigger.disabled {
  cursor: not-allowed;
  opacity: 0.7;
}

.upload-hint {
  margin: 8px 0 0;
  font-size: 12px;
  color: var(--ct-muted);
}
</style>

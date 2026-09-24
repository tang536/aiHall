<template>
  <div class="detail-page" v-loading="loading">
    <div class="page-header">
      <h2><el-icon><Goods /></el-icon> 商品详情</h2>
      <el-button text @click="$router.back()">
        <el-icon><Back /></el-icon> 返回列表
      </el-button>
    </div>

    <template v-if="item">
      <div class="detail-main">
        <!-- 左侧图片 -->
        <div class="gallery">
          <el-carousel v-if="images.length > 1" height="360px" indicator-position="outside">
            <el-carousel-item v-for="(url, idx) in images" :key="idx">
              <el-image :src="url" fit="contain" class="gallery-img" :preview-src-list="images" :initial-index="idx" />
            </el-carousel-item>
          </el-carousel>
          <el-image
            v-else-if="images.length === 1"
            :src="images[0]"
            fit="contain"
            class="gallery-single"
            :preview-src-list="images"
          />
          <div v-else class="gallery-empty">
            <el-icon :size="42"><Picture /></el-icon>
            <span>卖家未上传图片</span>
          </div>
        </div>

        <!-- 右侧信息 -->
        <div class="info-panel">
          <div class="title-row">
            <h3>{{ item.title }}</h3>
            <el-tag :type="MARKET_STATUS_TYPE[item.status]" effect="dark">
              {{ MARKET_STATUS_LABEL[item.status] || item.status }}
            </el-tag>
          </div>

          <div class="price-box">
            <span class="price">{{ formatMoney(item.price) }}</span>
            <span v-if="item.originalPrice" class="origin-price">原价 {{ formatMoney(item.originalPrice) }}</span>
          </div>

          <div class="attr-list">
            <div class="attr"><span class="k">成色</span><span class="v">{{ ITEM_CONDITION_LABEL[item.itemCondition] || '-' }}</span></div>
            <div class="attr"><span class="k">分类</span><span class="v">{{ MARKET_CATEGORY_LABEL[item.category] || '其他' }}</span></div>
            <div class="attr"><span class="k">交易地点</span><span class="v">{{ item.tradeLocation || '面议' }}</span></div>
            <div class="attr"><span class="k">上线日期</span><span class="v">{{ (item.onlineTime || item.createTime || '').replace('T', ' ').slice(0, 16) }}</span></div>
            <div class="attr"><span class="k">浏览 / 分享</span><span class="v">{{ item.viewCount || 0 }} / {{ item.shareCount || 0 }}</span></div>
            <div class="attr"><span class="k">我的余额</span><span class="v balance-text">{{ formatMoney(userStore.balance) }}</span></div>
          </div>

          <el-alert
            v-if="isMine"
            type="info"
            :closable="false"
            title="这是你发布的商品，不能购买自己的商品。"
            class="own-tip"
          />

          <div class="action-row">
            <el-button
              type="primary"
              size="large"
              :disabled="isMine || item.status !== 'ON_SALE' || buying"
              :loading="buying"
              @click="doBuy"
            >
              <el-icon><ShoppingCart /></el-icon>
              {{ item.status === 'SOLD' ? '已售出' : (item.status === 'OFF_SHELF' ? '已下架' : '立即购买') }}
            </el-button>
            <el-button size="large" @click="doShare">
              <el-icon><Share /></el-icon> 分享
            </el-button>
            <el-button
              size="large"
              :type="favorited ? 'danger' : 'default'"
              :loading="favoriting"
              @click="doFavorite"
            >
              <el-icon><Star /></el-icon> {{ favorited ? '已收藏' : '收藏' }}
            </el-button>
          </div>

          <el-divider content-position="left">卖家信息</el-divider>
          <UserCard
            :user="item.seller"
            show-chat
            show-add
            @chat="goChat"
            @add="addFriend"
          />
          <p class="privacy-tip">
            <el-icon><Lock /></el-icon>
            为保护隐私，仅展示脱敏后的学号；手机号与邮箱不会公开。
          </p>
        </div>
      </div>

      <!-- 描述 -->
      <div class="desc-card">
        <h4>商品描述</h4>
        <p class="desc-text">{{ item.description || '卖家暂未填写描述' }}</p>
      </div>
    </template>

    <el-empty v-else-if="!loading" description="商品不存在或已被删除" />

    <!-- 余额不足时引导充值 -->
    <el-dialog v-model="rechargeVisible" title="余额不足" width="420px">
      <p class="insufficient">
        当前余额 <strong>{{ formatMoney(userStore.balance) }}</strong>，
        本单需支付 <strong>{{ formatMoney(item?.price) }}</strong>。
      </p>
      <el-form label-width="70px">
        <el-form-item label="充值">
          <el-input-number v-model="rechargeAmount" :min="1" :max="10000" :precision="2" :step="50" />
          <span class="unit">元</span>
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="rechargeVisible = false">取消</el-button>
        <el-button type="primary" :loading="recharging" @click="doRecharge">充值并重试</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { computed, onMounted, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import UserCard from '@/components/UserCard.vue'
import { useUserStore } from '@/store/user'
import { getMarketItem, buyMarket, shareMarket, sendFriendRequest, recharge, getWallet, toggleFavorite, checkFavorite } from '@/api'
import {
  MARKET_CATEGORY_LABEL, ITEM_CONDITION_LABEL, MARKET_STATUS_LABEL, MARKET_STATUS_TYPE,
  splitImages, formatMoney
} from '@/utils/community'

const route = useRoute()
const router = useRouter()
const userStore = useUserStore()

const loading = ref(true)
const buying = ref(false)
const item = ref(null)
const rechargeVisible = ref(false)
const rechargeAmount = ref(100)
const recharging = ref(false)
const favorited = ref(false)
const favoriting = ref(false)

const images = computed(() => splitImages(item.value?.images))
const isMine = computed(() => item.value && Number(item.value.sellerId) === Number(userStore.userId))

async function load() {
  loading.value = true
  try {
    const res = await getMarketItem(route.params.id)
    item.value = res?.data || null
    // 检查收藏状态
    if (item.value && userStore.isLoggedIn) {
      try {
        const favRes = await checkFavorite(route.params.id)
        favorited.value = !!favRes?.data?.favorited
      } catch { /* 忽略 */ }
    } else {
      favorited.value = false
    }
  } catch {
    item.value = null
  } finally {
    loading.value = false
  }
}

async function doBuy() {
  if (!userStore.isLoggedIn) {
    ElMessage.warning('请先登录后再购买')
    router.push('/login')
    return
  }
  // 前端预判余额，避免无意义请求；真正的扣款仍由后端事务保证
  if (userStore.balance < Number(item.value.price)) {
    rechargeAmount.value = Math.ceil(Number(item.value.price))
    rechargeVisible.value = true
    return
  }
  try {
    await ElMessageBox.confirm(
      `确认支付 ${formatMoney(item.value.price)} 购买「${item.value.title}」吗？支付后金额将从余额扣除。`,
      '购买确认',
      { confirmButtonText: '确认购买', cancelButtonText: '取消', type: 'warning' }
    )
  } catch {
    return
  }
  buying.value = true
  try {
    await buyMarket(item.value.id)
    ElMessage.success('购买成功，请尽快与卖家联系完成线下交易')
    item.value.status = 'SOLD'
    // 以服务端余额为准，避免本地计算与事务结果不一致
    await refreshBalance()
  } catch (e) {
    if (String(e?.message || '').includes('余额不足')) {
      rechargeAmount.value = Math.ceil(Number(item.value.price))
      rechargeVisible.value = true
    }
  } finally {
    buying.value = false
  }
}

/** 从服务端同步最新余额 */
async function refreshBalance() {
  try {
    const res = await getWallet()
    userStore.setBalance(res?.data?.balance ?? userStore.balance)
  } catch {
    /* 静默失败，保留本地值 */
  }
}

async function doShare() {
  let shareUrl = ''
  try {
    const res = await shareMarket(item.value.id)
    shareUrl = res?.data?.shareUrl || `${window.location.origin}/market/${item.value.id}`
    item.value.shareCount = res?.data?.shareCount ?? (item.value.shareCount || 0) + 1
  } catch {
    shareUrl = `${window.location.origin}/market/${item.value.id}`
  }
  // 优先使用系统分享面板，桌面浏览器回退为复制链接
  if (navigator.share) {
    try {
      await navigator.share({ title: item.value.title, text: `校园二手：${item.value.title}`, url: shareUrl })
      return
    } catch {
      /* 用户取消则继续走复制 */
    }
  }
  try {
    await navigator.clipboard.writeText(shareUrl)
    ElMessage.success('商品链接已复制，快分享给同学吧')
  } catch {
    ElMessageBox.alert(shareUrl, '复制链接分享', { confirmButtonText: '知道了' })
  }
}

async function doFavorite() {
  if (!userStore.isLoggedIn) {
    router.push('/login')
    return
  }
  favoriting.value = true
  try {
    const res = await toggleFavorite(item.value.id)
    favorited.value = !!res?.data?.favorited
    ElMessage.success(favorited.value ? '已加入收藏' : '已取消收藏')
  } catch {
    /* 拦截器已提示 */
  } finally {
    favoriting.value = false
  }
}

function goChat(user) {
  if (!userStore.isLoggedIn) {
    router.push('/login')
    return
  }
  router.push({ path: '/messages', query: { peerId: user.userId } })
}

async function addFriend(user) {
  if (!userStore.isLoggedIn) {
    router.push('/login')
    return
  }
  try {
    const res = await sendFriendRequest({ userId: user.userId })
    ElMessage.success(res?.message || '好友申请已发送')
    item.value.seller.friend = true
  } catch {
    /* 错误提示由拦截器统一处理 */
  }
}

async function doRecharge() {
  const amount = Number(rechargeAmount.value)
  if (!amount || amount <= 0) {
    ElMessage.warning('请输入正确的充值金额')
    return
  }
  recharging.value = true
  try {
    const res = await recharge(amount)
    userStore.setBalance(res?.data?.balance ?? amount)
    ElMessage.success('充值成功')
    rechargeVisible.value = false
  } finally {
    recharging.value = false
  }
}

onMounted(() => {
  load()
  if (userStore.isLoggedIn) refreshBalance()
})
</script>

<style scoped>
.detail-main {
  display: grid;
  grid-template-columns: minmax(0, 1.05fr) minmax(0, 1fr);
  gap: 22px;
  align-items: start;
}

.gallery {
  background: var(--ct-white);
  border: 1px solid var(--ct-line);
  border-radius: 0;
  padding: 12px;
}

.gallery-img,
.gallery-single {
  width: 100%;
  height: 360px;
  display: block;
  background: #faf7f2;
  border-radius: 0;
}

.gallery-empty {
  height: 360px;
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  gap: 10px;
  color: #a8a49c;
  background: #faf7f2;
  border-radius: 0;
}

.info-panel {
  background: var(--ct-white);
  border: 1px solid var(--ct-line);
  border-radius: 0;
  padding: 18px;
}

.title-row {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 12px;
}

.title-row h3 {
  margin: 0;
  font-size: 19px;
  line-height: 1.45;
  color: var(--ct-ink);
}

.price-box {
  display: flex;
  align-items: baseline;
  gap: 10px;
  margin: 14px 0;
  padding: 12px 14px;
  border-radius: 0;
  background: var(--ct-red-soft);
}

.price {
  color: var(--ct-red);
  font-size: 27px;
  font-weight: 700;
}

.origin-price {
  color: #a8a49c;
  font-size: 13px;
  text-decoration: line-through;
}

.attr-list {
  display: grid;
  gap: 8px;
  margin-bottom: 14px;
}

.attr {
  display: flex;
  font-size: 13px;
}

.attr .k {
  width: 88px;
  color: var(--ct-muted);
  flex-shrink: 0;
}

.attr .v {
  color: var(--ct-ink);
  word-break: break-all;
}

.balance-text {
  color: var(--ct-gold);
  font-weight: 600;
}

.own-tip {
  margin-bottom: 12px;
}

.action-row {
  display: flex;
  gap: 10px;
  margin-bottom: 6px;
}

.privacy-tip {
  display: flex;
  align-items: center;
  gap: 5px;
  margin: 10px 0 0;
  font-size: 12px;
  color: #a8a49c;
}

.desc-card {
  margin-top: 20px;
  background: var(--ct-white);
  border: 1px solid var(--ct-line);
  border-radius: 0;
  padding: 18px;
}

.desc-card h4 {
  margin: 0 0 10px;
  font-size: 15px;
  color: var(--ct-ink);
}

.desc-text {
  margin: 0;
  color: #4a4845;
  line-height: 1.8;
  white-space: pre-wrap;
}

.insufficient {
  margin: 0 0 12px;
  color: #4a4845;
}

.unit {
  margin-left: 8px;
  color: var(--ct-muted);
  font-size: 12px;
}

@media (max-width: 900px) {
  .detail-main {
    grid-template-columns: 1fr;
  }
  .gallery-img,
  .gallery-single,
  .gallery-empty {
    height: 260px;
  }
}
</style>

<template>
  <div class="p-2">
    <transition :enter-active-class="proxy?.animate.searchAnimate.enter" :leave-active-class="proxy?.animate.searchAnimate.leave">
      <div v-show="showSearch" class="mb-[10px]">
        <el-card shadow="hover">
          <el-form ref="queryFormRef" :model="queryParams" :inline="true">
            <el-form-item label="门店" prop="storeId">
              <el-select v-model="queryParams.storeId" placeholder="全部门店" clearable filterable style="width: 180px">
                <el-option v-for="store in storeOptions" :key="store.storeId" :label="store.storeName" :value="store.storeId" />
              </el-select>
            </el-form-item>
            <el-form-item label="会员手机号" prop="mobile">
              <el-input v-model="queryParams.mobile" placeholder="手机号模糊" clearable style="width: 150px" @keyup.enter="handleQuery" />
            </el-form-item>
            <el-form-item label="会员昵称" prop="nickName">
              <el-input v-model="queryParams.nickName" placeholder="昵称模糊" clearable style="width: 130px" @keyup.enter="handleQuery" />
            </el-form-item>
            <el-form-item label="充值方式" prop="payType">
              <el-select v-model="queryParams.payType" placeholder="全部方式" clearable style="width: 140px">
                <el-option v-for="dict in member_pay_type" :key="dict.value" :label="dict.label" :value="dict.value" />
              </el-select>
            </el-form-item>
            <el-form-item label="状态" prop="status">
              <el-select v-model="queryParams.status" placeholder="全部状态" clearable style="width: 120px">
                <el-option label="有效" value="0" />
                <el-option label="已冲正" value="1" />
              </el-select>
            </el-form-item>
            <el-form-item label="充值时间">
              <el-date-picker
                v-model="dateRange"
                type="daterange"
                range-separator="-"
                start-placeholder="开始日期"
                end-placeholder="结束日期"
                value-format="YYYY-MM-DD"
                style="width: 240px"
              />
            </el-form-item>
            <el-form-item>
              <el-button type="primary" icon="Search" @click="handleQuery">搜索</el-button>
              <el-button icon="Refresh" @click="resetQuery">重置</el-button>
            </el-form-item>
          </el-form>
        </el-card>
      </div>
    </transition>

    <el-card shadow="hover">
      <template #header>
        <el-row :gutter="10" class="mb8">
          <right-toolbar v-model:show-search="showSearch" @query-table="getList"></right-toolbar>
        </el-row>
      </template>

      <el-table v-loading="loading" border :data="logList">
        <el-table-column label="流水号" align="center" prop="rechargeNo" width="180" show-overflow-tooltip />
        <el-table-column label="充值时间" align="center" prop="createTime" width="160">
          <template #default="scope">
            <span>{{ proxy.parseTime(scope.row.createTime) }}</span>
          </template>
        </el-table-column>
        <el-table-column label="门店" align="center" prop="storeName" min-width="120" show-overflow-tooltip />
        <el-table-column label="会员" align="left" min-width="150" show-overflow-tooltip>
          <template #default="scope">
            <div class="member-cell">
              <span class="member-nick">{{ scope.row.nickName || '未命名' }}</span>
              <span class="member-mobile">{{ maskMobile(scope.row.mobile) }}</span>
            </div>
          </template>
        </el-table-column>
        <el-table-column label="充值金额" align="right" prop="amount" width="100">
          <template #default="scope">
            <span>¥{{ money(scope.row.amount) }}</span>
          </template>
        </el-table-column>
        <el-table-column label="赠送金额" align="right" prop="giftAmount" width="100">
          <template #default="scope">
            <span>¥{{ money(scope.row.giftAmount) }}</span>
          </template>
        </el-table-column>
        <el-table-column label="到账合计" align="right" width="110">
          <template #default="scope">
            <b>¥{{ money(scope.row.totalAmount ?? Number(scope.row.amount) + Number(scope.row.giftAmount)) }}</b>
          </template>
        </el-table-column>
        <el-table-column label="充值方式" align="center" prop="payType" width="100">
          <template #default="scope">
            <dict-tag :options="member_pay_type" :value="scope.row.payType" />
          </template>
        </el-table-column>
        <el-table-column label="操作店员" align="center" prop="operatorName" width="100" />
        <el-table-column label="状态" align="center" width="90">
          <template #default="scope">
            <el-tag v-if="scope.row.status === '0'" type="success">有效</el-tag>
            <el-tag v-else type="info">已冲正</el-tag>
          </template>
        </el-table-column>
        <el-table-column label="操作" width="100" align="center" class-name="small-padding fixed-width">
          <template #default="scope">
            <el-button
              v-if="scope.row.status === '0' && isToday(scope.row.createTime)"
              v-hasPermi="['ops:recharge:reversal']"
              link
              type="danger"
              @click="handleReversal(scope.row)"
              >冲正</el-button
            >
            <el-tooltip v-else-if="scope.row.status === '1'" content="已冲正流水不可再冲正" placement="top">
              <span style="color: var(--el-text-color-placeholder)">—</span>
            </el-tooltip>
            <el-tooltip v-else content="仅限冲正当日充值（服务器自然日）" placement="top">
              <span style="color: var(--el-text-color-placeholder)">过期</span>
            </el-tooltip>
          </template>
        </el-table-column>
      </el-table>

      <pagination v-show="total > 0" v-model:page="queryParams.pageNum" v-model:limit="queryParams.pageSize" :total="total" @pagination="getList" />

      <el-alert
        class="mt-[10px]"
        type="info"
        :closable="false"
        title="冲正规则：仅限冲正当日（服务器自然日 00:00 起）的有效充值，仅可冲正一次，需会员余额充足。"
        description="冲正金额 = 充值本金 + 赠送金额（从会员余额扣除），冲正原因必填并计入余额流水；积分/累计消费不随充值冲正变动。"
      />
    </el-card>

    <!-- 冲正确认 弹窗 -->
    <el-dialog v-model="reversalDialog.visible" title="充值冲正确认" width="520px" append-to-body>
      <el-descriptions :column="2" border>
        <el-descriptions-item label="流水号" :span="2">{{ reversalDialog.row?.rechargeNo }}</el-descriptions-item>
        <el-descriptions-item label="会员"
          >{{ reversalDialog.row?.nickName || '未命名' }}（{{ maskMobile(reversalDialog.row?.mobile) }}）</el-descriptions-item
        >
        <el-descriptions-item label="门店">{{ reversalDialog.row?.storeName }}</el-descriptions-item>
        <el-descriptions-item label="充值时间">{{ proxy.parseTime(reversalDialog.row?.createTime) }}</el-descriptions-item>
        <el-descriptions-item label="操作店员">{{ reversalDialog.row?.operatorName }}</el-descriptions-item>
        <el-descriptions-item label="冲正动作" :span="2">
          会员余额
          <b>-¥{{ money(reversalDialog.row?.totalAmount ?? Number(reversalDialog.row?.amount) + Number(reversalDialog.row?.giftAmount)) }}</b> （本金
          ¥{{ money(reversalDialog.row?.amount) }} + 赠送 ¥{{ money(reversalDialog.row?.giftAmount) }}）
        </el-descriptions-item>
      </el-descriptions>
      <el-alert class="mt-[12px]" type="warning" :closable="false" title="限制：仅当日、仅一次、余额不足时冲正失败；充值即时撤销不可恢复。" />
      <el-form ref="reversalFormRef" :model="reversalForm" :rules="reversalRules" label-width="90px" class="mt-[12px]">
        <el-form-item label="冲正原因" prop="reason">
          <el-input
            v-model="reversalForm.reason"
            type="textarea"
            placeholder="必填，不超过 100 字，如「录入金额错误」"
            maxlength="100"
            show-word-limit
          />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button type="danger" :loading="reversalDialog.submitting" @click="submitReversal">确认冲正</el-button>
        <el-button @click="reversalDialog.visible = false">取 消</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup name="Recharge" lang="ts">
import { listRechargeLog, reversalRecharge } from '@/api/ops/member';
import { RechargeLogQuery, RechargeLogVO } from '@/api/ops/member/types';
import { optionselect } from '@/api/ops/store';
import { StoreVO } from '@/api/ops/store/types';

const { proxy } = getCurrentInstance() as ComponentInternalInstance;
const { member_pay_type } = toRefs<any>(proxy?.useDict('member_pay_type'));

const logList = ref<RechargeLogVO[]>([]);
const storeOptions = ref<StoreVO[]>([]);
const loading = ref(true);
const showSearch = ref(true);
const total = ref(0);
const dateRange = ref<[string, string] | null>(null);
const queryFormRef = ref<ElFormInstance>();
const reversalFormRef = ref<ElFormInstance>();

const queryParams = ref<RechargeLogQuery>({
  pageNum: 1,
  pageSize: 10,
  storeId: '',
  mobile: '',
  nickName: '',
  payType: '',
  status: '',
  beginTime: '',
  endTime: ''
});

const reversalDialog = reactive<{ visible: boolean; submitting: boolean; row?: RechargeLogVO }>({ visible: false, submitting: false });
const reversalForm = reactive({ reason: '' });
const reversalRules = {
  reason: [
    { required: true, message: '冲正原因不能为空', trigger: 'blur' },
    { max: 100, message: '冲正原因不能超过 100 字', trigger: 'blur' }
  ]
};

const maskMobile = (mobile?: string) => (mobile ? mobile.replace(/^(\d{3})\d{4}(\d{4})$/, '$1****$2') : '—');
const money = (value?: number | string) => Number(value ?? 0).toFixed(2);

/** 是否当日（服务器自然日口径，与后端校验一致） */
const isToday = (time?: string) => {
  if (!time) return false;
  return new Date(time).toDateString() === new Date().toDateString();
};

/** 查询充值流水分页 */
const getList = async () => {
  loading.value = true;
  queryParams.value.beginTime = dateRange.value?.[0] ? dateRange.value[0] + ' 00:00:00' : '';
  queryParams.value.endTime = dateRange.value?.[1] ? dateRange.value[1] + ' 23:59:59' : '';
  const res = await listRechargeLog(queryParams.value);
  logList.value = res.rows;
  total.value = res.total;
  loading.value = false;
};

/** 加载门店下拉（仅启用门店；停用店历史流水可按其他条件筛选） */
const loadStoreOptions = async () => {
  const res = await optionselect();
  storeOptions.value = res.data;
};

/** 搜索按钮操作 */
const handleQuery = () => {
  queryParams.value.pageNum = 1;
  getList();
};

/** 重置按钮操作 */
const resetQuery = () => {
  queryFormRef.value?.resetFields();
  dateRange.value = null;
  handleQuery();
};

/** 冲正确认弹窗 */
const handleReversal = (row: RechargeLogVO) => {
  reversalDialog.row = row;
  reversalForm.reason = '';
  reversalFormRef.value?.resetFields();
  reversalDialog.visible = true;
};

const submitReversal = () => {
  reversalFormRef.value?.validate(async (valid: boolean) => {
    if (!valid) return;
    reversalDialog.submitting = true;
    try {
      await reversalRecharge(reversalDialog.row!.rechargeId, reversalForm.reason);
      proxy?.$modal.msgSuccess('冲正成功，会员余额已扣除到账合计');
      reversalDialog.visible = false;
      await getList();
    } finally {
      reversalDialog.submitting = false;
    }
  });
};

onMounted(() => {
  getList();
  loadStoreOptions();
});
</script>

<style scoped>
.member-cell {
  display: flex;
  flex-direction: column;
  line-height: 18px;
}
.member-nick {
  font-weight: 600;
}
.member-mobile {
  font-size: 12px;
  color: var(--el-text-color-secondary);
}
</style>

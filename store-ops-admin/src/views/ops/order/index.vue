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
            <el-form-item label="状态" prop="status">
              <el-select v-model="queryParams.status" placeholder="全部状态" clearable style="width: 120px">
                <el-option v-for="dict in order_status" :key="dict.value" :label="dict.label" :value="dict.value" />
              </el-select>
            </el-form-item>
            <el-form-item label="支付方式" prop="payType">
              <el-select v-model="queryParams.payType" placeholder="全部方式" clearable style="width: 140px">
                <el-option v-for="dict in order_pay_type" :key="dict.value" :label="dict.label" :value="dict.value" />
              </el-select>
            </el-form-item>
            <el-form-item label="订单号" prop="orderNo">
              <el-input v-model="queryParams.orderNo" placeholder="订单号模糊" clearable style="width: 160px" @keyup.enter="handleQuery" />
            </el-form-item>
            <el-form-item label="会员手机号" prop="memberMobile">
              <el-input v-model="queryParams.memberMobile" placeholder="手机号模糊" clearable style="width: 150px" @keyup.enter="handleQuery" />
            </el-form-item>
            <el-form-item label="开单时间">
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

      <el-table v-loading="loading" border :data="orderList">
        <el-table-column label="订单号" align="center" prop="orderNo" width="180" show-overflow-tooltip />
        <el-table-column label="开单时间" align="center" prop="createTime" width="160">
          <template #default="scope">
            <span>{{ proxy.parseTime(scope.row.createTime) }}</span>
          </template>
        </el-table-column>
        <el-table-column label="门店" align="center" prop="storeName" min-width="110" show-overflow-tooltip />
        <el-table-column label="会员" align="left" min-width="140" show-overflow-tooltip>
          <template #default="scope">
            <div class="member-cell">
              <span class="member-nick">{{ scope.row.memberName || '散客' }}</span>
              <span v-if="scope.row.memberMobile" class="member-mobile">{{ maskMobile(scope.row.memberMobile) }}</span>
            </div>
          </template>
        </el-table-column>
        <el-table-column label="总额" align="right" prop="totalAmount" width="95">
          <template #default="scope">
            <span>¥{{ money(scope.row.totalAmount) }}</span>
          </template>
        </el-table-column>
        <el-table-column label="优惠" align="right" width="90">
          <template #default="scope">
            <span v-if="Number(scope.row.couponAmount) > 0">-¥{{ money(scope.row.couponAmount) }}</span>
            <span v-else style="color: var(--el-text-color-placeholder)">—</span>
          </template>
        </el-table-column>
        <el-table-column label="应付" align="right" width="100">
          <template #default="scope">
            <b>¥{{ money(scope.row.payAmount) }}</b>
          </template>
        </el-table-column>
        <el-table-column label="支付方式" align="center" width="100">
          <template #default="scope">
            <dict-tag v-if="scope.row.payType" :options="order_pay_type" :value="scope.row.payType" />
            <span v-else style="color: var(--el-text-color-placeholder)">—</span>
          </template>
        </el-table-column>
        <el-table-column label="状态" align="center" width="90">
          <template #default="scope">
            <dict-tag :options="order_status" :value="scope.row.status" />
          </template>
        </el-table-column>
        <el-table-column label="本单积分" align="right" prop="earnedPoint" width="90">
          <template #default="scope">
            <span v-if="Number(scope.row.earnedPoint) > 0">{{ scope.row.earnedPoint }}</span>
            <span v-else style="color: var(--el-text-color-placeholder)">—</span>
          </template>
        </el-table-column>
        <el-table-column label="开单人" align="center" prop="operatorName" width="90" />
        <el-table-column label="操作" width="190" align="center" class-name="small-padding fixed-width">
          <template #default="scope">
            <el-button link type="primary" @click="handleDetail(scope.row)">详情</el-button>
            <el-button v-if="scope.row.status === '1'" v-hasPermi="['ops:order:remark']" link type="primary" @click="handleRemark(scope.row)"
              >备注</el-button
            >
            <el-button v-if="scope.row.status === '0'" v-hasPermi="['ops:order:void']" link type="danger" @click="handleVoid(scope.row)"
              >作废</el-button
            >
            <el-button
              v-if="scope.row.status === '1' && scope.row.payType === '1' && scope.row.balanceReversed === '0' && isToday(scope.row.createTime)"
              v-hasPermi="['ops:order:reversal']"
              link
              type="danger"
              @click="handleReversal(scope.row)"
              >余额冲正</el-button
            >
          </template>
        </el-table-column>
      </el-table>

      <pagination v-show="total > 0" v-model:page="queryParams.pageNum" v-model:limit="queryParams.pageSize" :total="total" @pagination="getList" />

      <el-alert
        class="mt-[10px]"
        type="info"
        :closable="false"
        title="订单规则：已收款订单不可作废、不可退款；待收款订单可由店长作废（原因必填）。"
        description="余额收款错账：仅限当日（服务器自然日）、仅可冲正一次，冲正将等额加回会员余额，不动积分与累计消费；订单状态不变，冲正痕迹自动入备注。开单与收款在小程序工作台完成，本页不提供开单。"
      />
    </el-card>

    <!-- 订单详情 弹窗 -->
    <el-dialog v-model="detailDialog.visible" title="订单详情" width="720px" append-to-body>
      <el-descriptions :column="2" border>
        <el-descriptions-item label="订单号">{{ detail.orderNo }}</el-descriptions-item>
        <el-descriptions-item label="开单时间">{{ proxy.parseTime(detail.createTime) }}</el-descriptions-item>
        <el-descriptions-item label="门店">{{ detail.storeName }}</el-descriptions-item>
        <el-descriptions-item label="开单人">{{ detail.operatorName }}</el-descriptions-item>
        <el-descriptions-item label="会员">
          {{ detail.memberName || '散客' }}<span v-if="detail.memberMobile">（{{ maskMobile(detail.memberMobile) }}）</span>
        </el-descriptions-item>
        <el-descriptions-item label="状态">
          <dict-tag :options="order_status" :value="detail.status" />
        </el-descriptions-item>
        <el-descriptions-item label="总额">¥{{ money(detail.totalAmount) }}</el-descriptions-item>
        <el-descriptions-item label="优惠">
          <span v-if="Number(detail.couponAmount) > 0">-¥{{ money(detail.couponAmount) }}（{{ detail.couponName || '优惠券' }}）</span>
          <span v-else>—</span>
        </el-descriptions-item>
        <el-descriptions-item label="应付">¥{{ money(detail.payAmount) }}</el-descriptions-item>
        <el-descriptions-item label="支付方式">
          <dict-tag v-if="detail.payType" :options="order_pay_type" :value="detail.payType" />
          <span v-else>—</span>
        </el-descriptions-item>
        <el-descriptions-item label="本单积分">{{ Number(detail.earnedPoint) > 0 ? detail.earnedPoint : '—' }}</el-descriptions-item>
        <el-descriptions-item label="余额冲正">
          <el-tag v-if="detail.balanceReversed === '1'" type="danger">已冲正</el-tag>
          <span v-else>否</span>
        </el-descriptions-item>
        <el-descriptions-item v-if="detail.status === '2'" label="作废原因" :span="2">{{ detail.voidReason }}</el-descriptions-item>
      </el-descriptions>

      <el-table :data="detail.items || []" border class="mt-[12px]" size="small">
        <el-table-column label="项目名称" align="left" min-width="160">
          <template #default="scope">
            <span>{{ scope.row.itemName || '未命名项目' }}</span>
          </template>
        </el-table-column>
        <el-table-column label="单价" align="right" width="110">
          <template #default="scope">¥{{ money(scope.row.unitPrice) }}</template>
        </el-table-column>
        <el-table-column label="数量" align="center" prop="quantity" width="80" />
        <el-table-column label="行金额" align="right" width="110">
          <template #default="scope">¥{{ money(scope.row.lineAmount) }}</template>
        </el-table-column>
      </el-table>

      <el-descriptions v-if="detail.remark" :column="1" border class="mt-[12px]">
        <el-descriptions-item label="备注">
          <div style="white-space: pre-line">{{ detail.remark }}</div>
        </el-descriptions-item>
      </el-descriptions>
      <template #footer>
        <el-button @click="detailDialog.visible = false">关 闭</el-button>
      </template>
    </el-dialog>

    <!-- 作废 弹窗 -->
    <el-dialog v-model="voidDialog.visible" title="订单作废" width="520px" append-to-body>
      <el-descriptions :column="2" border>
        <el-descriptions-item label="订单号">{{ voidDialog.row?.orderNo }}</el-descriptions-item>
        <el-descriptions-item label="应付金额">¥{{ money(voidDialog.row?.payAmount) }}</el-descriptions-item>
      </el-descriptions>
      <el-alert class="mt-[12px]" type="warning" :closable="false" title="作废后订单不可恢复，不产生任何余额/积分变动；仅待收款订单可作废。" />
      <el-form ref="voidFormRef" :model="voidForm" :rules="reasonRules" label-width="90px" class="mt-[12px]">
        <el-form-item label="作废原因" prop="reason">
          <el-input v-model="voidForm.reason" type="textarea" placeholder="必填，不超过 200 字，如「顾客取消消费」" maxlength="200" show-word-limit />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button type="danger" :loading="voidDialog.submitting" @click="submitVoid">确认作废</el-button>
        <el-button @click="voidDialog.visible = false">取 消</el-button>
      </template>
    </el-dialog>

    <!-- 备注 弹窗 -->
    <el-dialog v-model="remarkDialog.visible" title="追加备注" width="520px" append-to-body>
      <el-alert class="mb-[12px]" type="info" :closable="false" title="备注为追加式（自动带时间与操作人），仅错账追溯用，累计上限 255 字。" />
      <el-form ref="remarkFormRef" :model="remarkForm" :rules="remarkRules" label-width="90px">
        <el-form-item label="备注内容" prop="remark">
          <el-input
            v-model="remarkForm.remark"
            type="textarea"
            placeholder="必填，不超过 100 字，如「顾客实付与系统差异 2 元，已线下核对」"
            maxlength="100"
            show-word-limit
          />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button type="primary" :loading="remarkDialog.submitting" @click="submitRemark">确 定</el-button>
        <el-button @click="remarkDialog.visible = false">取 消</el-button>
      </template>
    </el-dialog>

    <!-- 余额冲正 弹窗 -->
    <el-dialog v-model="reversalDialog.visible" title="余额收款冲正确认" width="520px" append-to-body>
      <el-descriptions :column="2" border>
        <el-descriptions-item label="订单号">{{ reversalDialog.row?.orderNo }}</el-descriptions-item>
        <el-descriptions-item label="开单时间">{{ proxy.parseTime(reversalDialog.row?.createTime) }}</el-descriptions-item>
        <el-descriptions-item label="会员">{{ reversalDialog.row?.memberName || '散客' }}</el-descriptions-item>
        <el-descriptions-item label="冲正动作">
          会员余额 <b>+¥{{ money(reversalDialog.row?.payAmount) }}</b>
        </el-descriptions-item>
      </el-descriptions>
      <el-alert class="mt-[12px]" type="warning" :closable="false" title="限制：仅当日、仅一次；积分与累计消费不回退，订单状态保持已完成。" />
      <el-form ref="reversalFormRef" :model="reversalForm" :rules="reasonRules" label-width="90px" class="mt-[12px]">
        <el-form-item label="冲正原因" prop="reason">
          <el-input
            v-model="reversalForm.reason"
            type="textarea"
            placeholder="必填，不超过 200 字，如「余额收款金额录错」"
            maxlength="200"
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

<script setup name="Order" lang="ts">
import { getOrder, listOrder, remarkOrder, reversalOrderBalance, voidOrder } from '@/api/ops/order';
import { OrderQuery, OrderVO } from '@/api/ops/order/types';
import { optionselect } from '@/api/ops/store';
import { StoreVO } from '@/api/ops/store/types';

const { proxy } = getCurrentInstance() as ComponentInternalInstance;
const { order_status, order_pay_type } = toRefs<any>(proxy?.useDict('order_status', 'order_pay_type'));

const orderList = ref<OrderVO[]>([]);
const storeOptions = ref<StoreVO[]>([]);
const loading = ref(true);
const showSearch = ref(true);
const total = ref(0);
const dateRange = ref<[string, string] | null>(null);
const queryFormRef = ref<ElFormInstance>();
const voidFormRef = ref<ElFormInstance>();
const remarkFormRef = ref<ElFormInstance>();
const reversalFormRef = ref<ElFormInstance>();

const queryParams = ref<OrderQuery>({
  pageNum: 1,
  pageSize: 10,
  storeId: '',
  status: '',
  payType: '',
  orderNo: '',
  memberMobile: '',
  beginTime: '',
  endTime: ''
});

const detail = ref<OrderVO>({} as OrderVO);
const detailDialog = reactive({ visible: false });

const voidDialog = reactive<{ visible: boolean; submitting: boolean; row?: OrderVO }>({ visible: false, submitting: false });
const voidForm = reactive({ reason: '' });

const remarkDialog = reactive<{ visible: boolean; submitting: boolean; row?: OrderVO }>({ visible: false, submitting: false });
const remarkForm = reactive({ remark: '' });
const remarkRules = {
  remark: [
    { required: true, message: '备注内容不能为空', trigger: 'blur' },
    { max: 100, message: '备注内容不能超过 100 字', trigger: 'blur' }
  ]
};

const reversalDialog = reactive<{ visible: boolean; submitting: boolean; row?: OrderVO }>({ visible: false, submitting: false });
const reversalForm = reactive({ reason: '' });

const reasonRules = {
  reason: [
    { required: true, message: '原因不能为空', trigger: 'blur' },
    { max: 200, message: '原因不能超过 200 字', trigger: 'blur' }
  ]
};

const maskMobile = (mobile?: string) => (mobile ? mobile.replace(/^(\d{3})\d{4}(\d{4})$/, '$1****$2') : '—');
const money = (value?: number | string) => Number(value ?? 0).toFixed(2);

/** 是否当日（服务器自然日口径，与后端校验一致） */
const isToday = (time?: string) => {
  if (!time) return false;
  return new Date(time).toDateString() === new Date().toDateString();
};

/** 查询订单分页 */
const getList = async () => {
  loading.value = true;
  queryParams.value.beginTime = dateRange.value?.[0] ? dateRange.value[0] + ' 00:00:00' : '';
  queryParams.value.endTime = dateRange.value?.[1] ? dateRange.value[1] + ' 23:59:59' : '';
  const res = await listOrder(queryParams.value);
  orderList.value = res.rows;
  total.value = res.total;
  loading.value = false;
};

/** 加载门店下拉（仅启用门店；停用店历史订单可按其他条件筛选） */
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

/** 详情弹窗（后端拉全量，含明细与最新备注） */
const handleDetail = async (row: OrderVO) => {
  const res = await getOrder(row.orderId);
  detail.value = res.data;
  detailDialog.visible = true;
};

/** 作废弹窗 */
const handleVoid = (row: OrderVO) => {
  voidDialog.row = row;
  voidForm.reason = '';
  voidFormRef.value?.resetFields();
  voidDialog.visible = true;
};

const submitVoid = () => {
  voidFormRef.value?.validate(async (valid: boolean) => {
    if (!valid) return;
    voidDialog.submitting = true;
    try {
      await voidOrder(voidDialog.row!.orderId, voidForm.reason);
      proxy?.$modal.msgSuccess('订单已作废');
      voidDialog.visible = false;
      await getList();
    } finally {
      voidDialog.submitting = false;
    }
  });
};

/** 备注弹窗 */
const handleRemark = (row: OrderVO) => {
  remarkDialog.row = row;
  remarkForm.remark = '';
  remarkFormRef.value?.resetFields();
  remarkDialog.visible = true;
};

const submitRemark = () => {
  remarkFormRef.value?.validate(async (valid: boolean) => {
    if (!valid) return;
    remarkDialog.submitting = true;
    try {
      await remarkOrder(remarkDialog.row!.orderId, remarkForm.remark);
      proxy?.$modal.msgSuccess('备注已追加');
      remarkDialog.visible = false;
      await getList();
    } finally {
      remarkDialog.submitting = false;
    }
  });
};

/** 余额冲正弹窗 */
const handleReversal = (row: OrderVO) => {
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
      await reversalOrderBalance(reversalDialog.row!.orderId, reversalForm.reason);
      proxy?.$modal.msgSuccess('冲正成功，会员余额已加回应付金额');
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

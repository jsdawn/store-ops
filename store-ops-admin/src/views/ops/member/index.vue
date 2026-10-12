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
            <el-form-item label="手机号" prop="mobile">
              <el-input v-model="queryParams.mobile" placeholder="手机号前缀" clearable style="width: 160px" @keyup.enter="handleQuery" />
            </el-form-item>
            <el-form-item label="卡号" prop="memberNo">
              <el-input v-model="queryParams.memberNo" placeholder="会员卡号" clearable style="width: 140px" @keyup.enter="handleQuery" />
            </el-form-item>
            <el-form-item label="昵称" prop="nickName">
              <el-input v-model="queryParams.nickName" placeholder="昵称模糊" clearable style="width: 140px" @keyup.enter="handleQuery" />
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
          <el-col :span="1.5">
            <el-button v-hasPermi="['ops:member:add']" type="primary" plain icon="Plus" @click="handleAdd">线下登记</el-button>
          </el-col>
          <right-toolbar v-model:show-search="showSearch" @query-table="getList"></right-toolbar>
        </el-row>
      </template>

      <el-table v-loading="loading" border :data="memberList">
        <el-table-column label="会员" align="left" min-width="170" show-overflow-tooltip>
          <template #default="scope">
            <div class="member-cell">
              <span class="member-nick">{{ scope.row.nickName || '未命名' }}</span>
              <span class="member-mobile">{{ maskMobile(scope.row.mobile) }}</span>
            </div>
          </template>
        </el-table-column>
        <el-table-column label="门店" align="center" prop="storeName" min-width="120" show-overflow-tooltip />
        <el-table-column label="卡号" align="center" prop="memberNo" width="110" />
        <el-table-column label="余额（元）" align="right" prop="balance" width="110">
          <template #default="scope">
            <b>{{ money(scope.row.balance) }}</b>
          </template>
        </el-table-column>
        <el-table-column label="积分" align="right" prop="pointBalance" width="100">
          <template #default="scope">
            <span>{{ money(scope.row.pointBalance) }}</span>
          </template>
        </el-table-column>
        <el-table-column label="微信绑定" align="center" width="90">
          <template #default="scope">
            <el-tag v-if="scope.row.wxBound" type="success">已绑定</el-tag>
            <el-tag v-else type="info">未绑定</el-tag>
          </template>
        </el-table-column>
        <el-table-column label="累计消费" align="center" min-width="130">
          <template #default="scope">
            <span>¥{{ money(scope.row.totalAmount) }} / {{ scope.row.totalOrders }} 次</span>
          </template>
        </el-table-column>
        <el-table-column label="开卡时间" align="center" prop="createTime" width="120">
          <template #default="scope">
            <span>{{ proxy.parseTime(scope.row.createTime, '{y}-{m}-{d}') }}</span>
          </template>
        </el-table-column>
        <el-table-column label="操作" width="150" align="center" class-name="small-padding fixed-width">
          <template #default="scope">
            <el-button v-hasPermi="['ops:member:query']" link type="primary" icon="View" @click="handleDetail(scope.row)">详情</el-button>
            <el-button v-hasPermi="['ops:member:recharge']" link type="warning" icon="Coin" @click="handleRecharge(scope.row)">充值</el-button>
          </template>
        </el-table-column>
      </el-table>

      <pagination v-show="total > 0" v-model:page="queryParams.pageNum" v-model:limit="queryParams.pageSize" :total="total" @pagination="getList" />

      <el-alert
        class="mt-[10px]"
        type="info"
        :closable="false"
        title="会员规则：会员挂门店（各店独立会员池），手机号+卡号均店内唯一；档案不可编辑，录错手机号只能弃用重建。"
        description="充值 = 线下收款后记账式入账（本金+赠送合并入余额），不支持撤销，错账走「充值流水」页当日冲正；积分由订单完成后按实付 1:1 自动累计。"
      />
    </el-card>

    <!-- 线下登记 弹窗 -->
    <el-dialog v-model="addDialog.visible" title="线下登记会员" width="460px" append-to-body>
      <el-form ref="memberFormRef" :model="form" :rules="memberRules" label-width="90px">
        <el-form-item label="所属门店" prop="storeId">
          <el-select v-model="form.storeId" placeholder="请选择门店" style="width: 100%" filterable>
            <el-option v-for="store in storeOptions" :key="store.storeId" :label="store.storeName" :value="store.storeId" />
          </el-select>
          <div class="form-tip">会员档案挂在所选门店下，卡号由系统按「门店序号+店内序号」自动生成</div>
        </el-form-item>
        <el-form-item label="手机号" prop="mobile">
          <el-input v-model="form.mobile" placeholder="11 位手机号，店内唯一" maxlength="11" />
          <div class="form-tip">重复手机号请先用搜索定位已有档案，不重复建档</div>
        </el-form-item>
        <el-form-item label="昵称" prop="nickName">
          <el-input v-model="form.nickName" placeholder="选填；顾客绑定微信后自动带出微信昵称" maxlength="30" />
        </el-form-item>
        <el-form-item label="备注" prop="remark">
          <el-input v-model="form.remark" type="textarea" placeholder="选填，不超过 100 字" maxlength="100" show-word-limit />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button type="primary" :loading="addDialog.submitting" @click="submitAdd">登 记</el-button>
        <el-button @click="addDialog.visible = false">取 消</el-button>
      </template>
    </el-dialog>

    <!-- 会员详情 弹窗 -->
    <el-dialog v-model="detailDialog.visible" title="会员详情" width="560px" append-to-body>
      <el-descriptions :column="2" border>
        <el-descriptions-item label="昵称">{{ detailDialog.member?.nickName || '未命名' }}</el-descriptions-item>
        <el-descriptions-item label="手机号">{{ maskMobile(detailDialog.member?.mobile) }}</el-descriptions-item>
        <el-descriptions-item label="卡号">{{ detailDialog.member?.memberNo }}</el-descriptions-item>
        <el-descriptions-item label="门店">{{ detailDialog.member?.storeName }}</el-descriptions-item>
        <el-descriptions-item label="余额">
          <b>¥{{ money(detailDialog.member?.balance) }}</b>
        </el-descriptions-item>
        <el-descriptions-item label="积分">{{ money(detailDialog.member?.pointBalance) }}</el-descriptions-item>
        <el-descriptions-item label="累计消费"
          >¥{{ money(detailDialog.member?.totalAmount) }} / {{ detailDialog.member?.totalOrders }} 次</el-descriptions-item
        >
        <el-descriptions-item label="微信绑定">
          <el-tag v-if="detailDialog.member?.wxBound" type="success">已绑定</el-tag>
          <el-tag v-else type="info">未绑定</el-tag>
        </el-descriptions-item>
        <el-descriptions-item label="开卡时间" :span="2">{{ proxy.parseTime(detailDialog.member?.createTime) }}</el-descriptions-item>
        <el-descriptions-item label="备注" :span="2">{{ detailDialog.member?.remark || '—' }}</el-descriptions-item>
      </el-descriptions>
      <template #footer>
        <el-button @click="detailDialog.visible = false">关 闭</el-button>
      </template>
    </el-dialog>

    <!-- 充值 弹窗 -->
    <el-dialog v-model="rechargeDialog.visible" title="充值入账" width="480px" append-to-body>
      <el-descriptions :column="1" border class="mb-[12px]">
        <el-descriptions-item label="会员">
          {{ rechargeDialog.form.nickName || '未命名' }}（{{ maskMobile(rechargeDialog.form.mobile) }} · 卡号 {{ rechargeDialog.form.memberNo }} ·
          {{ rechargeDialog.form.storeName }}）
        </el-descriptions-item>
      </el-descriptions>
      <el-form ref="rechargeFormRef" :model="rechargeDialog.form" :rules="rechargeRules" label-width="90px">
        <el-form-item label="充值金额" prop="amount">
          <el-input-number
            v-model="rechargeDialog.form.amount"
            :min="0.01"
            :max="99999.99"
            :precision="2"
            :step="100"
            style="width: 200px"
            controls-position="right"
          />
          <span class="form-tip" style="margin-left: 8px">线下已收款金额（元）</span>
        </el-form-item>
        <el-form-item label="赠送金额" prop="giftAmount">
          <el-input-number
            v-model="rechargeDialog.form.giftAmount"
            :min="0"
            :max="99999.99"
            :precision="2"
            :step="10"
            style="width: 200px"
            controls-position="right"
          />
          <span class="form-tip" style="margin-left: 8px">0 ~ 本金，与本金合并入余额</span>
        </el-form-item>
        <el-form-item label="充值方式" prop="payType">
          <el-radio-group v-model="rechargeDialog.form.payType">
            <el-radio v-for="dict in member_pay_type" :key="dict.value" :value="dict.value">{{ dict.label }}</el-radio>
          </el-radio-group>
          <div class="form-tip">仅记录收款方式，不触发任何线上支付</div>
        </el-form-item>
        <el-form-item label="备注" prop="remark">
          <el-input
            v-model="rechargeDialog.form.remark"
            type="textarea"
            placeholder="选填，如「周年庆充 500 送 50」"
            maxlength="100"
            show-word-limit
          />
        </el-form-item>
      </el-form>
      <el-alert type="warning" :closable="false" title="入账即时生效且不支持撤销；错账请当日到「充值流水」页冲正。" />
      <template #footer>
        <el-button type="primary" :loading="rechargeDialog.submitting" @click="submitRecharge">确认入账</el-button>
        <el-button @click="rechargeDialog.visible = false">取 消</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup name="Member" lang="ts">
import { addMember, getMember, listMember, rechargeMember } from '@/api/ops/member';
import { MemberForm, MemberQuery, MemberVO, RechargeForm } from '@/api/ops/member/types';
import { optionselect } from '@/api/ops/store';
import { StoreVO } from '@/api/ops/store/types';

const { proxy } = getCurrentInstance() as ComponentInternalInstance;
const { member_pay_type } = toRefs<any>(proxy?.useDict('member_pay_type'));

const memberList = ref<MemberVO[]>([]);
const storeOptions = ref<StoreVO[]>([]);
const loading = ref(true);
const showSearch = ref(true);
const total = ref(0);
const queryFormRef = ref<ElFormInstance>();
const memberFormRef = ref<ElFormInstance>();
const rechargeFormRef = ref<ElFormInstance>();

const addDialog = reactive<{ visible: boolean; submitting: boolean }>({ visible: false, submitting: false });
const detailDialog = reactive<{ visible: boolean; member?: MemberVO }>({ visible: false });
const rechargeDialog = reactive<{ visible: boolean; submitting: boolean; form: RechargeForm }>({
  visible: false,
  submitting: false,
  form: initRechargeForm()
});

const initMemberForm: MemberForm = {
  storeId: undefined,
  mobile: '',
  nickName: '',
  remark: ''
};

const data = reactive<PageData<MemberForm, MemberQuery>>({
  form: { ...initMemberForm },
  queryParams: {
    pageNum: 1,
    pageSize: 10,
    storeId: '',
    mobile: '',
    memberNo: '',
    nickName: ''
  },
  rules: {}
});

const { queryParams, form, rules } = toRefs<PageData<MemberForm, MemberQuery>>(data);

const memberRules = {
  storeId: [{ required: true, message: '所属门店不能为空', trigger: 'change' }],
  mobile: [
    { required: true, message: '手机号不能为空', trigger: 'blur' },
    { pattern: /^1[3-9]\d{9}$/, message: '手机号格式不正确', trigger: 'blur' }
  ]
};

const rechargeRules = {
  amount: [{ required: true, message: '充值金额不能为空', trigger: 'blur' }],
  giftAmount: [{ required: true, message: '赠送金额不能为空', trigger: 'blur' }],
  payType: [{ required: true, message: '充值方式不能为空', trigger: 'change' }]
};

function initRechargeForm(): RechargeForm {
  return {
    memberId: '',
    memberNo: '',
    nickName: '',
    mobile: '',
    storeName: '',
    amount: undefined,
    giftAmount: 0,
    payType: '',
    remark: ''
  };
}

/** 手机号脱敏 138****6688 */
const maskMobile = (mobile?: string) => (mobile ? mobile.replace(/^(\d{3})\d{4}(\d{4})$/, '$1****$2') : '—');

/** 金额两位小数展示 */
const money = (value?: number | string) => Number(value ?? 0).toFixed(2);

/** 查询会员列表 */
const getList = async () => {
  loading.value = true;
  const res = await listMember(queryParams.value);
  memberList.value = res.rows;
  total.value = res.total;
  loading.value = false;
};

/** 加载门店下拉（仅启用门店） */
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
  handleQuery();
};

/** 线下登记 */
const handleAdd = () => {
  form.value = { ...initMemberForm };
  memberFormRef.value?.resetFields();
  loadStoreOptions();
  addDialog.visible = true;
};

const submitAdd = () => {
  memberFormRef.value?.validate(async (valid: boolean) => {
    if (valid) {
      addDialog.submitting = true;
      try {
        await addMember(form.value);
        proxy?.$modal.msgSuccess('登记成功，卡号已自动生成');
        addDialog.visible = false;
        await getList();
      } finally {
        addDialog.submitting = false;
      }
    }
  });
};

/** 会员详情 */
const handleDetail = async (row: MemberVO) => {
  const res = await getMember(row.memberId);
  detailDialog.member = res.data;
  detailDialog.visible = true;
};

/** 充值 */
const handleRecharge = (row: MemberVO) => {
  rechargeDialog.form = initRechargeForm();
  Object.assign(rechargeDialog.form, {
    memberId: row.memberId,
    memberNo: row.memberNo,
    nickName: row.nickName,
    mobile: row.mobile,
    storeName: row.storeName,
    giftAmount: 0,
    payType: ''
  });
  rechargeFormRef.value?.resetFields();
  rechargeDialog.visible = true;
};

const submitRecharge = () => {
  rechargeFormRef.value?.validate(async (valid: boolean) => {
    if (!valid) return;
    if (Number(rechargeDialog.form.giftAmount) > Number(rechargeDialog.form.amount)) {
      proxy?.$modal.msgWarning('赠送金额不能大于充值本金');
      return;
    }
    rechargeDialog.submitting = true;
    try {
      await rechargeMember(rechargeDialog.form);
      proxy?.$modal.msgSuccess('充值入账成功');
      rechargeDialog.visible = false;
      await getList();
    } finally {
      rechargeDialog.submitting = false;
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
.form-tip {
  width: 100%;
  font-size: 12px;
  line-height: 18px;
  color: var(--el-text-color-secondary);
  margin-top: 2px;
}
</style>

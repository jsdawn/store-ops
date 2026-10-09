<template>
  <div class="p-2">
    <transition :enter-active-class="proxy?.animate.searchAnimate.enter" :leave-active-class="proxy?.animate.searchAnimate.leave">
      <div v-show="showSearch" class="mb-[10px]">
        <el-card shadow="hover">
          <el-form ref="queryFormRef" :model="queryParams" :inline="true">
            <el-form-item label="门店名称" prop="storeName">
              <el-input v-model="queryParams.storeName" placeholder="请输入门店名称" clearable @keyup.enter="handleQuery" />
            </el-form-item>
            <el-form-item label="状态" prop="status">
              <el-select v-model="queryParams.status" placeholder="全部状态" clearable style="width: 160px">
                <el-option v-for="dict in sys_normal_disable" :key="dict.value" :label="dict.label" :value="dict.value" />
              </el-select>
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
            <el-button v-hasPermi="['ops:store:add']" type="primary" plain icon="Plus" @click="handleAdd">新建门店</el-button>
          </el-col>
          <right-toolbar v-model:show-search="showSearch" @query-table="getList"></right-toolbar>
        </el-row>
      </template>

      <el-table v-loading="loading" border :data="storeList">
        <el-table-column label="门店名称" align="center" prop="storeName" min-width="140" show-overflow-tooltip>
          <template #default="scope">
            <b>{{ scope.row.storeName }}</b>
          </template>
        </el-table-column>
        <el-table-column label="绑定部门" align="center" prop="deptName" min-width="160" show-overflow-tooltip />
        <el-table-column label="地址" align="center" prop="address" min-width="180" show-overflow-tooltip>
          <template #default="scope">
            <span v-if="scope.row.address">{{ scope.row.address }}</span>
            <span v-else style="color: var(--el-text-color-placeholder)">—</span>
          </template>
        </el-table-column>
        <el-table-column label="小程序码" align="center" width="100">
          <template #default="scope">
            <el-button v-if="scope.row.status === '0'" v-hasPermi="['ops:store:query']" link type="primary" @click="handleQrcode(scope.row)"
              >查看</el-button
            >
            <span v-else style="color: var(--el-text-color-placeholder)">已停用</span>
          </template>
        </el-table-column>
        <el-table-column label="状态" align="center" prop="status" width="90">
          <template #default="scope">
            <dict-tag :options="sys_normal_disable" :value="scope.row.status" />
          </template>
        </el-table-column>
        <el-table-column label="创建时间" align="center" prop="createTime" width="120">
          <template #default="scope">
            <span>{{ proxy.parseTime(scope.row.createTime, '{y}-{m}-{d}') }}</span>
          </template>
        </el-table-column>
        <el-table-column label="操作" width="140" align="center" class-name="small-padding fixed-width">
          <template #default="scope">
            <el-button v-hasPermi="['ops:store:edit']" link type="primary" icon="Edit" @click="handleUpdate(scope.row)">编辑</el-button>
            <el-button v-if="scope.row.status === '0'" v-hasPermi="['ops:store:edit']" link type="danger" @click="handleStop(scope.row)"
              >停用</el-button
            >
            <el-button v-else v-hasPermi="['ops:store:edit']" link type="success" @click="handleEnable(scope.row)">启用</el-button>
          </template>
        </el-table-column>
      </el-table>

      <pagination v-show="total > 0" v-model:page="queryParams.pageNum" v-model:limit="queryParams.pageSize" :total="total" @pagination="getList" />

      <el-alert
        class="mt-[10px]"
        type="info"
        :closable="false"
        title="绑定规则：先在框架「部门管理」建门店部门，再在此关联——1 部门 ↔ 1 门店，表单仅可选未绑定门店的部门。"
        description="店长不在本页指定，走员工挂部门 + 角色配置；本期不提供删除门店，仅停用（会员/订单/流水保留，重新启用后恢复）。"
      />
    </el-card>

    <!-- 新建/编辑门店 抽屉 -->
    <el-drawer v-model="drawer.visible" :title="drawer.title" size="480px" append-to-body>
      <el-form ref="storeFormRef" :model="form" :rules="rules" label-width="90px">
        <el-form-item label="门店名称" prop="storeName">
          <el-input v-model="form.storeName" placeholder="请输入门店名称" maxlength="20" show-word-limit />
          <div class="form-tip">不超过 20 字，租户内不重复</div>
        </el-form-item>
        <el-form-item label="所属部门" prop="deptId">
          <el-select v-model="form.deptId" placeholder="请选择未绑定门店的部门" style="width: 100%" :disabled="drawer.isEdit" clearable>
            <el-option v-for="dept in unboundDepts" :key="dept.deptId" :label="dept.deptName" :value="dept.deptId" />
          </el-select>
          <div v-if="!drawer.isEdit" class="form-tip">
            先建部门再关联：部门在框架「部门管理」维护，下拉为空时请先去部门管理新建门店部门；1 个部门只能绑定 1 家门店。
          </div>
          <div v-else class="form-tip">门店一经创建绑定部门不可更换；建错部门可停用后重新建店（详设 5.3）。</div>
        </el-form-item>
        <el-form-item label="门店地址" prop="address">
          <el-input v-model="form.address" placeholder="选填" maxlength="50" show-word-limit />
        </el-form-item>
        <el-form-item label="状态" prop="status">
          <el-radio-group v-model="form.status">
            <el-radio v-for="dict in sys_normal_disable" :key="dict.value" :value="dict.value">{{ dict.label }}</el-radio>
          </el-radio-group>
          <div class="form-tip">启用后小程序码立即可扫、顾客可进店建档</div>
        </el-form-item>
        <el-form-item label="备注" prop="remark">
          <el-input v-model="form.remark" type="textarea" placeholder="选填，不超过 100 字" maxlength="100" show-word-limit />
        </el-form-item>
      </el-form>
      <template #footer>
        <div style="text-align: right">
          <el-button type="primary" @click="submitForm">保 存</el-button>
          <el-button @click="cancel">取 消</el-button>
        </div>
      </template>
    </el-drawer>

    <!-- 停用门店确认 弹窗 -->
    <el-dialog v-model="stopDialog.visible" title="停用门店确认" width="520px" append-to-body>
      <el-descriptions :column="1" border>
        <el-descriptions-item label="门店">{{ stopDialog.storeName }}</el-descriptions-item>
        <el-descriptions-item label="顾客端">扫码提示「门店暂停营业」，不可建档、不可兑该店券</el-descriptions-item>
        <el-descriptions-item label="商家端">该店工作台收银 / 充值 / 核销不可用</el-descriptions-item>
        <el-descriptions-item label="数据">会员 / 订单 / 流水保留，重新启用后恢复；本期不提供删除</el-descriptions-item>
      </el-descriptions>
      <template #footer>
        <el-button type="danger" @click="confirmStop">确认停用</el-button>
        <el-button @click="stopDialog.visible = false">取 消</el-button>
      </template>
    </el-dialog>

    <!-- 小程序码 弹窗（P1 占位） -->
    <el-dialog v-model="qrcodeDialog.visible" title="门店小程序码" width="420px" append-to-body>
      <div class="qrcode-placeholder">
        <el-icon :size="64" color="var(--el-text-color-placeholder)"><Grid /></el-icon>
        <div class="qrcode-scene">scene = {{ qrcodeDialog.scene }}</div>
        <div class="qrcode-tip">{{ qrcodeDialog.tip }}</div>
      </div>
      <template #footer>
        <el-button type="primary" disabled>下载</el-button>
        <el-button @click="qrcodeDialog.visible = false">关 闭</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup name="Store" lang="ts">
import { changeStoreStatus, getStore, getStoreQrcode, listStore, listUnboundDepts, addStore, updateStore } from '@/api/ops/store';
import { StoreDeptOption, StoreForm, StoreQuery, StoreVO } from '@/api/ops/store/types';

const { proxy } = getCurrentInstance() as ComponentInternalInstance;
const { sys_normal_disable } = toRefs<any>(proxy?.useDict('sys_normal_disable'));

const storeList = ref<StoreVO[]>([]);
const loading = ref(true);
const showSearch = ref(true);
const total = ref(0);
const unboundDepts = ref<StoreDeptOption[]>([]);
const storeFormRef = ref<ElFormInstance>();
const queryFormRef = ref<ElFormInstance>();

const drawer = reactive<StoreDrawerOption>({
  visible: false,
  title: '',
  isEdit: false
});

const stopDialog = reactive<{ visible: boolean; storeId: string | number; storeName: string }>({
  visible: false,
  storeId: '',
  storeName: ''
});

const qrcodeDialog = reactive<{ visible: boolean; scene: string; tip: string }>({
  visible: false,
  scene: '',
  tip: ''
});

const initFormData: StoreForm = {
  storeId: undefined,
  deptId: undefined,
  storeName: '',
  address: '',
  status: '0',
  remark: ''
};

const data = reactive<PageData<StoreForm, StoreQuery>>({
  form: { ...initFormData },
  queryParams: {
    pageNum: 1,
    pageSize: 10,
    storeName: '',
    status: ''
  },
  rules: {
    storeName: [{ required: true, message: '门店名称不能为空', trigger: 'blur' }],
    deptId: [{ required: true, message: '所属部门不能为空', trigger: 'change' }],
    status: [{ required: true, message: '状态不能为空', trigger: 'change' }]
  }
});

const { queryParams, form, rules } = toRefs<PageData<StoreForm, StoreQuery>>(data);

/** 抽屉属性（新建/编辑共用） */
interface StoreDrawerOption {
  visible: boolean;
  title: string;
  isEdit: boolean;
}

/** 查询门店列表 */
const getList = async () => {
  loading.value = true;
  const res = await listStore(queryParams.value);
  storeList.value = res.rows;
  total.value = res.total;
  loading.value = false;
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

/** 加载未绑定部门下拉 */
const loadUnboundDepts = async () => {
  const res = await listUnboundDepts();
  unboundDepts.value = res.data;
};

/** 表单重置 */
const reset = () => {
  form.value = { ...initFormData };
  storeFormRef.value?.resetFields();
};

/** 取消按钮 */
const cancel = () => {
  drawer.visible = false;
};

/** 新建门店 */
const handleAdd = () => {
  reset();
  loadUnboundDepts();
  drawer.isEdit = false;
  drawer.title = '新建门店';
  drawer.visible = true;
};

/** 编辑门店 */
const handleUpdate = async (row: StoreVO) => {
  reset();
  const res = await getStore(row.storeId);
  Object.assign(form.value, res.data);
  form.value.deptId = res.data.deptId;
  drawer.isEdit = true;
  drawer.title = '编辑门店';
  drawer.visible = true;
};

/** 提交表单 */
const submitForm = () => {
  storeFormRef.value?.validate(async (valid: boolean) => {
    if (valid) {
      if (form.value.storeId) {
        await updateStore(form.value);
        proxy?.$modal.msgSuccess('保存成功：门店已绑定部门。店长请到「员工管理」把用户挂到该部门并配店长角色');
      } else {
        await addStore(form.value);
        proxy?.$modal.msgSuccess('已保存：门店已绑定部门。店长请到「员工管理」把用户挂到该部门并配店长角色');
      }
      drawer.visible = false;
      await getList();
    }
  });
};

/** 停用确认弹窗 */
const handleStop = (row: StoreVO) => {
  stopDialog.storeId = row.storeId;
  stopDialog.storeName = row.storeName;
  stopDialog.visible = true;
};

const confirmStop = async () => {
  await changeStoreStatus(stopDialog.storeId, '1');
  stopDialog.visible = false;
  proxy?.$modal.msgSuccess('已停用：该店小程序码与工作台均已停用，历史数据保留');
  await getList();
};

/** 启用 */
const handleEnable = async (row: StoreVO) => {
  await changeStoreStatus(row.storeId, '0');
  proxy?.$modal.msgSuccess('已启用：小程序码立即可扫，顾客可建档，工作台恢复收银');
  await getList();
};

/** 查看小程序码 */
const handleQrcode = async (row: StoreVO) => {
  const res = await getStoreQrcode(row.storeId);
  qrcodeDialog.scene = res.data.scene;
  qrcodeDialog.tip = res.data.tip;
  qrcodeDialog.visible = true;
};

onMounted(() => {
  getList();
});
</script>

<style scoped>
.form-tip {
  width: 100%;
  font-size: 12px;
  line-height: 18px;
  color: var(--el-text-color-secondary);
  margin-top: 2px;
}

.qrcode-placeholder {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 8px;
  padding: 24px 0;
}

.qrcode-scene {
  font-size: 14px;
  font-weight: 600;
  color: var(--el-text-color-primary);
}

.qrcode-tip {
  font-size: 12px;
  color: var(--el-text-color-secondary);
}
</style>

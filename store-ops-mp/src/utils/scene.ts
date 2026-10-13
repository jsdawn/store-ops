/**
 * 解析小程序码 scene 中的门店ID
 *
 * 兼容格式：'123' / 's=123' / 's_id=123' / 'storeId=123'
 * 注意：雪花ID超出 Number.MAX_SAFE_INTEGER，必须全程保持字符串，
 * 禁止 Number() 转换（会精度丢失，如 ...218 → ...200）。
 * @param scene 扫码进入时的 scene 参数（或直接传 storeId 的 query）
 * @returns 门店ID字符串，解析失败返回 undefined
 */
export function parseStoreId(scene?: string | number | null): string | undefined {
  if (scene == null)
    return undefined
  const raw = String(scene).trim()
  if (!raw)
    return undefined
  const m = raw.match(/^(?:s=|s_id=|store_id=)?(\d+)$/i)
  if (!m)
    return undefined
  // 前导零按无效处理（雪花ID不会以0开头），全零/空串拒绝
  return m[1] === '0' || /^0\d+$/.test(m[1]) ? undefined : m[1]
}

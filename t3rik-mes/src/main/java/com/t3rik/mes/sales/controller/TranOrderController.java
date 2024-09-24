package com.t3rik.mes.sales.controller;


import cn.hutool.core.collection.CollectionUtil;
import cn.hutool.core.lang.Assert;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.t3rik.common.annotation.Log;
import com.t3rik.common.constant.MsgConstants;
import com.t3rik.common.core.controller.BaseController;
import com.t3rik.common.core.domain.AjaxResult;
import com.t3rik.common.core.domain.CheckInfo;
import com.t3rik.common.core.page.TableDataInfo;
import com.t3rik.common.enums.BusinessType;
import com.t3rik.common.enums.mes.OrderStatusEnum;
import com.t3rik.common.exception.BusinessException;
import com.t3rik.common.utils.StringUtils;
import com.t3rik.common.utils.poi.ExcelUtil;
import com.t3rik.mes.pro.domain.ProClientOrderItem;
import com.t3rik.mes.pro.domain.ProWorkorder;
import com.t3rik.mes.sales.domain.SalesOrder;
import com.t3rik.mes.sales.domain.SalesOrderItem;
import com.t3rik.mes.sales.domain.TranOrder;
import com.t3rik.mes.sales.domain.TranOrderLine;
import com.t3rik.mes.sales.service.ITranOrderLineService;
import com.t3rik.mes.sales.service.ITranOrderService;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.text.MessageFormat;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * 销售送货单Controller
 *
 * @author t3rik
 * @date 2024-09-09
 */
@RestController
@RequestMapping("/sales/tranOrder")
public class TranOrderController extends BaseController {
    @Autowired
    private ITranOrderService tranOrderService;
    @Autowired
    private ITranOrderLineService tranOrderLineService;

    /**
     * 查询销售送货单列表
     */
    @PreAuthorize("@ss.hasPermi('sales:tranOrder:list')")
    @GetMapping("/list")
    public TableDataInfo list(TranOrder tranOrder) {
        // 获取查询条件
        LambdaQueryWrapper<TranOrder> queryWrapper = getQueryWrapper(tranOrder);
        // 组装分页
        Page<TranOrder> page = getMPPage(tranOrder);
        // 查询
        this.tranOrderService.page(page, queryWrapper);
        return getDataTableWithPage(page);
    }

    /**
     * 导出销售送货单列表
     */
    @PreAuthorize("@ss.hasPermi('sales:tranOrder:export')")
    @Log(title = "销售送货单", businessType = BusinessType.EXPORT)
    @PostMapping("/export")
    public void export(HttpServletResponse response, TranOrder tranOrder) {
        // 获取查询条件
        LambdaQueryWrapper<TranOrder> queryWrapper = getQueryWrapper(tranOrder);
        List<TranOrder> list = this.tranOrderService.list(queryWrapper);
        ExcelUtil<TranOrder> util = new ExcelUtil<TranOrder>(TranOrder. class);
        util.exportExcel(response, list, "销售送货单数据");
    }

    /**
     * 获取销售送货单详细信息
     */
    @PreAuthorize("@ss.hasPermi('sales:tranOrder:query')")
    @GetMapping(value = "/{tranOrderId}")
    public AjaxResult getInfo(@PathVariable("tranOrderId") Long tranOrderId) {
        TranOrder tranOrder=this.tranOrderService.getById(tranOrderId);

        LambdaQueryWrapper<TranOrderLine> queryWrapper = new LambdaQueryWrapper<>();
        queryWrapper.eq(tranOrder.getTranOrderId() != null, TranOrderLine::getTranOrderId, tranOrder.getTranOrderId());
        List<TranOrderLine> orderItemList= tranOrderLineService.list(queryWrapper);
        tranOrder.setTranOrderLineList(orderItemList);
        return AjaxResult.success(tranOrder);
    }

    /**
     * 新增销售送货单
     */
    @PreAuthorize("@ss.hasPermi('sales:tranOrder:add')")
    @Log(title = "销售送货单", businessType = BusinessType.INSERT)
    @PostMapping
    public AjaxResult add(@RequestBody TranOrder tranOrder) {
        this.tranOrderService.saveTranOrder(tranOrder);
        return success();
    }

    /**
     * 修改销售送货单
     */
    @PreAuthorize("@ss.hasPermi('sales:tranOrder:edit')")
    @Log(title = "销售送货单", businessType = BusinessType.UPDATE)
    @PutMapping
    @Transactional
    public AjaxResult edit(@RequestBody TranOrder tranOrder) {

        return AjaxResult.success(this.tranOrderService.updateTranOrder(tranOrder));
    }
//    /**
//     * 修改销售送货单
//     */
//    @PreAuthorize("@ss.hasPermi('sales:tranOrder:review')")
//    @Log(title = "销售送货单", businessType = BusinessType.UPDATE)
//    @PutMapping("/review")
//    @Transactional
//    public AjaxResult review(@RequestBody TranOrder tranOrder) {
//        this.tranOrderService.updateById(tranOrder);
//        if(tranOrder.getTranOrderLineList().size()>0){
//            tranOrderLineService.updateBatchById(tranOrder.getTranOrderLineList());
//        }
//        return success();
//    }

    /**
     * 删除销售送货单
     */
    @PreAuthorize("@ss.hasPermi('sales:tranOrder:remove')")
    @Log(title = "销售送货单", businessType = BusinessType.DELETE)
    @DeleteMapping("/{tranOrderIds}")
    public AjaxResult remove(@PathVariable List<Long> tranOrderIds) {
        StringBuffer sb=this.tranOrderService.deleteByIds(tranOrderIds);
        if(sb.length()>0){
            return AjaxResult.error(sb.toString());
        }else{
            return  AjaxResult.success();
        }
    }
    /**
     * 审批（提交、拒绝）
     */
    @PreAuthorize("@ss.hasPermi('sales:tranOrder:edit')")
    @Log(title = "销售订单审批", businessType = BusinessType.UPDATE)
    @Transactional
    @PutMapping("/refuse/{tranOrderId},{status}")
    public AjaxResult refuse(@PathVariable("tranOrderId") Long tranOrderId,@PathVariable("status") String status) {
        if (StringUtils.isNull(tranOrderId)) {
            return AjaxResult.error("请先保存单据");
        }
        TranOrder tranOrder=this.tranOrderService.getById(tranOrderId);
        tranOrder.setStatus(status);


        return AjaxResult.success(this.tranOrderService.refuse(tranOrder));
    }
    /**
     * 审批通过并执行相关操作
     */
    @PreAuthorize("@ss.hasPermi('sales:tranOrder:execute')")
    @Log(title = "销售送货订单", businessType = BusinessType.INSERT)
    @PostMapping("/execute/{tranOrderId}")
    @Transactional
    public AjaxResult generateWorkOrder(@PathVariable("tranOrderId") String salesOrderId) throws Exception {
        TranOrder tranOrder = this.tranOrderService.getById(salesOrderId);
        // 查询是否已经添加销列
        List<TranOrderLine> itemList = this.tranOrderLineService.lambdaQuery()
                .eq(TranOrderLine::getTranOrderId, tranOrder.getTranOrderId())
                .list();
        tranOrder.setTranOrderLineList(itemList);
        // 数据校验
        CheckInfo check = this.check(tranOrder);
        // 未通过校验
        Assert.isTrue(check.getIsCheckPassed(), () -> new BusinessException(check.getMsg()));

        return AjaxResult.success(this.tranOrderService.execute(tranOrder).toString());
    }

    /**
     * 生成生产订单数据校验方法
     */
    private CheckInfo check(TranOrder tranOrder) {
        CheckInfo checkInfo = new CheckInfo();
        // 默认未通过
        checkInfo.setIsCheckPassed(Boolean.FALSE);
        if (tranOrder == null) {
            checkInfo.setMsg(MsgConstants.PARAM_ERROR);
            return checkInfo;
        }
        // 查询是否已经添加销售列
        List<TranOrderLine> itemList = tranOrder.getTranOrderLineList();
        if (CollectionUtil.isEmpty(itemList)) {
            checkInfo.setMsg("此单据下没有送货列,请添加相关送货数据");
            return checkInfo;
        }else{
            // 校验通过
            checkInfo.setIsCheckPassed(Boolean.TRUE);
        }

        return checkInfo;
    }

    /**
     * 获取查询条件
     */
    public LambdaQueryWrapper<TranOrder> getQueryWrapper(TranOrder tranOrde) {
        LambdaQueryWrapper<TranOrder> queryWrapper = new LambdaQueryWrapper<>();
        queryWrapper.eq(tranOrde.getTranOrderCode() != null, TranOrder::getTranOrderCode, tranOrde.getTranOrderCode());
        queryWrapper.eq(tranOrde.getWarehouseId() != null, TranOrder::getWarehouseId, tranOrde.getWarehouseId());
        queryWrapper.eq(tranOrde.getTranOrderType() != null, TranOrder::getTranOrderType, tranOrde.getTranOrderType());
        queryWrapper.eq(tranOrde.getStatus() != null ,TranOrder::getStatus, tranOrde.getStatus());
        queryWrapper.eq(tranOrde.getClientId() != null, TranOrder::getClientId, tranOrde.getClientId());
        queryWrapper.eq(tranOrde.getClientCode() != null, TranOrder::getClientCode, tranOrde.getClientCode());
        queryWrapper.like(StringUtils.isNotEmpty(tranOrde.getClientName()), TranOrder::getClientName, tranOrde.getClientName());
        queryWrapper.eq(tranOrde.getTotalAmount() != null, TranOrder::getTotalAmount, tranOrde.getTotalAmount());
        queryWrapper.eq(tranOrde.getWeight() != null, TranOrder::getWeight, tranOrde.getWeight());
        queryWrapper.eq(tranOrde.getCurrency() != null, TranOrder::getCurrency, tranOrde.getCurrency());
        queryWrapper.eq(tranOrde.getPayUp() != null, TranOrder::getPayUp, tranOrde.getPayUp());
        queryWrapper.like(StringUtils.isNotEmpty(tranOrde.getFollowerMan()), TranOrder::getFollowerMan, tranOrde.getFollowerMan());
        queryWrapper.like(StringUtils.isNotEmpty(tranOrde.getBusMan()), TranOrder::getBusMan, tranOrde.getBusMan());
        queryWrapper.eq(tranOrde.getCreateBy() != null, TranOrder::getCreateBy, tranOrde.getCreateBy());
        queryWrapper.eq(tranOrde.getCreateTime() != null, TranOrder::getCreateTime, tranOrde.getCreateTime());
        queryWrapper.eq(tranOrde.getUpdateBy() != null, TranOrder::getUpdateBy, tranOrde.getUpdateBy());
        queryWrapper.eq(tranOrde.getUpdateTime() != null, TranOrder::getUpdateTime, tranOrde.getUpdateTime());
        queryWrapper.eq(tranOrde.getRemark() != null, TranOrder::getRemark, tranOrde.getRemark());
        // 默认创建时间倒序
        queryWrapper.orderByDesc(TranOrder::getCreateTime);
        Map<String, Object> params = tranOrde.getParams();
        queryWrapper.between(params.get("beginTime") != null && params.get("endTime") != null,TranOrder::getCreateTime, params.get("beginTime"), params.get("endTime"));
        return queryWrapper;
    }
}

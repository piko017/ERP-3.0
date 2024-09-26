package com.t3rik.mes.sales.service.impl;

import cn.hutool.core.bean.BeanUtil;
import cn.hutool.core.collection.CollectionUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.toolkit.CollectionUtils;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.t3rik.common.constant.UserConstants;
import com.t3rik.common.enums.mes.ItemTypeEnum;
import com.t3rik.common.enums.mes.OrderStatusEnum;
import com.t3rik.common.enums.mes.WorkOrderSourceTypeEnum;
import com.t3rik.common.enums.mes.WorkOrderTypeEnum;
import com.t3rik.common.utils.StringUtils;
import com.t3rik.mes.api.service.IMesWorkOrderService;
import com.t3rik.mes.common.service.IAsyncService;
import com.t3rik.mes.md.domain.MdProductBom;
import com.t3rik.mes.md.service.IMdProductBomService;
import com.t3rik.mes.pro.domain.ProClientOrderItem;
import com.t3rik.mes.pro.domain.ProWorkorder;
import com.t3rik.mes.pro.domain.ProWorkorderBom;
import com.t3rik.mes.pro.service.IProClientOrderItemService;
import com.t3rik.mes.pro.service.IProWorkorderBomService;
import com.t3rik.mes.pro.service.IProWorkorderService;
import com.t3rik.mes.sales.domain.SalesOrder;
import com.t3rik.mes.sales.domain.SalesOrderItem;
import com.t3rik.mes.sales.mapper.SalesOrderMapper;
import com.t3rik.mes.sales.service.ISalesOrderItemService;
import com.t3rik.mes.sales.service.ISalesOrderService;
import com.t3rik.mes.wm.domain.WmMaterialStock;
import com.t3rik.mes.wm.service.IWmMaterialStockService;
import com.t3rik.system.strategy.AutoCodeUtil;
import jakarta.annotation.Resource;
import org.jetbrains.annotations.NotNull;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * 销售订单Service业务层处理
 *
 * @author t3rik
 * @date 2024-08-29
 */
@Service
public class SalesOrderServiceImpl extends ServiceImpl<SalesOrderMapper, SalesOrder> implements ISalesOrderService {
    @Autowired
    private SalesOrderMapper salesOrderMapper;
    @Autowired
    private ISalesOrderItemService salesOrderItemService;
    @Autowired
    private IMdProductBomService productBomService;
    @Resource
    private IProWorkorderService proWorkorderService;
    @Autowired
    private IProClientOrderItemService clientOrderItemService;
    @Resource
    private IProWorkorderBomService workorderBomService;
    @Resource
    private IAsyncService asyncService;
    @Autowired
    private AutoCodeUtil autoCodeUtil;
    @Resource
    private IMesWorkOrderService IMesWorkOrderService;
    @Autowired
    private IWmMaterialStockService wmMaterialStockService;

    @Override
    @Transactional
    public boolean saveOrder(SalesOrder salesOrder) {
        if (salesOrder.getSalesOrderItemList().size() > 0) {
            this.save(salesOrder);
            this.buildSalesOrderItems(salesOrder).stream().forEach(f -> {
                if (f.getSalesOrderDate() == null) {
                    f.setSalesOrderDate(salesOrder.getSalesOrderDate());
                }
                if (f.getDeliveryDate() == null) {
                    f.setDeliveryDate(salesOrder.getDeliveryDate());
                }
                this.salesOrderItemService.save(f);
                this.buildItemProduct(f);
            });
            return true;
        } else {
            this.save(salesOrder);
            return true;
        }
    }

    /**
     * 构建订单子项
     */
    @NotNull
    private List<SalesOrderItem> buildSalesOrderItems(SalesOrder salesOrder) {
        List<SalesOrderItem> itemList = new ArrayList<>();
        for (SalesOrderItem obj : salesOrder.getSalesOrderItemList()) {
            obj.setSalesOrderId(salesOrder.getSalesOrderId());
            obj.setSalesOrderCode(salesOrder.getSalesOrderCode());
            obj.setOweQty(obj.getSalesOrderQuantity());
            obj.setSalesOrderItemCode(autoCodeUtil.genSerialCode("SALES_ITEM_CODE", null));
            if (salesOrder.getStatus() != null) {
                obj.setStatus(salesOrder.getStatus());
            }
            // 查询库存
            WmMaterialStock wmMaterialStock = new WmMaterialStock();
            wmMaterialStock.setItemId(obj.getProductId());
            wmMaterialStock.setItemCode(obj.getProductCode());
            wmMaterialStock.setItemName(obj.getProductName());
            List<WmMaterialStock> list = wmMaterialStockService.selectWmMaterialStockList(wmMaterialStock);
            if (list.size() > 0) {
                StringBuffer sb = new StringBuffer();
                list.forEach(f -> {
                    sb.append(f.getWarehouseName() + ":" + f.getQuantityOnhand());
                });
                obj.setStockNum(sb.toString());
            }
//            salesOrderItemService.save(obj);
            itemList.add(obj);

        }
        return itemList;
    }

    /**
     * 订单子项产品是否有bom
     */
    private void buildItemProduct(SalesOrderItem salesOrderItem) {
        // 查询当前产品是否存在bom组合,如果存在,写入到客户订单物料表中
        List<MdProductBom> productBoms = productBomService.lambdaQuery().
                eq(MdProductBom::getItemId, salesOrderItem.getProductId())
                .list();
        // 如果存在bom组合,写入到客户订单物料表中
        if (CollectionUtil.isNotEmpty(productBoms)) {
            List<ProClientOrderItem> proClientOrderItems = buildClientOrderItems(salesOrderItem, productBoms);
            // 保存数据
            this.clientOrderItemService.saveBatch(proClientOrderItems);
        }
    }

    /**
     * 构建客户订单子项
     */
    @NotNull
    private List<ProClientOrderItem> buildClientOrderItems(@NotNull SalesOrderItem salesOrderItem, @NotNull List<MdProductBom> productBoms) {
        // 获取物料层级
        AtomicInteger levelIndex = new AtomicInteger(1);
        return productBoms.stream().map(productBom -> {
            ProClientOrderItem orderItem = new ProClientOrderItem();
            // 需求数量不复制
            BeanUtil.copyProperties(productBom, orderItem, "quantity");
            // 物料主键
            orderItem.setItemId(productBom.getBomItemId());
            orderItem.setItemCode(productBom.getBomItemCode());
            orderItem.setItemName(productBom.getBomItemName());
            orderItem.setSpecification(productBom.getBomItemSpec());
            // 客户订单表主键
            orderItem.setClientOrderId(salesOrderItem.getSalesOrderItemId());
            // 如果级别为空 并且级别小于等于4级,才会添加级别,超出的数据级别为空
            if (StringUtils.isBlank(productBom.getLevel()) && levelIndex.get() <= 4) {
                // 添加级别
                orderItem.setLevel(String.valueOf(levelIndex.getAndAdd(1)));
            }
            return orderItem;
        }).toList();
    }

    /**
     * 生成生产订单
     */
    @Transactional
    @Override
    public List<ProWorkorder> execute(SalesOrder salesOrder) throws Exception {
        List<SalesOrderItem> salesOrderItems = salesOrder.getSalesOrderItemList();
        List<ProWorkorder> proWorkorderList = new ArrayList<>();
        salesOrderItems.stream().forEach(f -> {
            // 生产订单
            ProWorkorder workorder = buildWorkOrder(f, salesOrder.getOrderType());
            // 保存生成工单
            this.proWorkorderService.save(workorder);
            // 查询对应的客户订单子项
            List<ProClientOrderItem> clientOrderItems =
                    this.clientOrderItemService.lambdaQuery()
                            .eq(ProClientOrderItem::getClientOrderId, f.getSalesOrderItemId())
                            .list();
            // 构建工单相应的bom列表
            List<ProWorkorderBom> workorderBomList = buildWorkBomList(workorder, clientOrderItems);
            // 保存工单相应的bom列表
            this.workorderBomService.saveBatch(workorderBomList);
            // 回写客户订单
            SalesOrderItem salesOrderItem = buildClientOrder(f, workorder);
            // 更新客户订单
            this.salesOrderItemService.updateById(salesOrderItem);
            // 回写客户订单物料信息
            this.clientOrderItemService.lambdaUpdate()
                    .ge(ProClientOrderItem::getClientOrderId, f.getSalesOrderItemId())
                    .set(ProClientOrderItem::getWorkorderId, workorder.getWorkorderId())
                    .set(ProClientOrderItem::getWorkorderCode, workorder.getWorkorderCode())
                    .set(ProClientOrderItem::getWorkorderName, workorder.getWorkorderName())
                    .update();
            // 异步回写产品对应的bom列表
            this.asyncService.updateItemBomAndLevel(f.getProductId(), clientOrderItems);
            proWorkorderList.add(workorder);

        });
        salesOrder.setStatus("WORK_ORDER_FINISHED");
        this.updateById(salesOrder);
        return proWorkorderList;
    }

    @Override
    @Transactional
    public boolean updateSalesOrder(SalesOrder salesOrder) {
        if (salesOrder.getStatus().equals(OrderStatusEnum.REFUSE.getCode())) {
            salesOrder.setStatus(OrderStatusEnum.APPROVING.getCode());
        }
        List<SalesOrderItem> orderItemList = getItemList(salesOrder);
        this.salesOrderItemService.removeByIds(orderItemList);

        this.updateById(salesOrder);
        if (salesOrder.getSalesOrderItemList().size() > 0) {
            this.buildSalesOrderItems(salesOrder).stream().forEach(f -> {
                if (f.getSalesOrderDate() == null) {
                    f.setSalesOrderDate(salesOrder.getSalesOrderDate());
                }
                if (f.getDeliveryDate() == null) {
                    f.setDeliveryDate(salesOrder.getDeliveryDate());
                }
                this.salesOrderItemService.save(f);
                this.buildItemProduct(f);
            });
        }
        return true;
    }

    @Override
    @Transactional
    public StringBuffer deleteByIds(List<Long> salesOrderIds) {
        List<SalesOrder> salesOrders = this.listByIds(salesOrderIds);
        // -t3rik
        // 没有线程安全的问题的情况下 用 StringBuilder
        StringBuffer sb = new StringBuffer();
        for (SalesOrder li : salesOrders) {
            List<SalesOrderItem> orderItemList = getItemList(li);
            // -t3rik
            // 建议多用工具类做集合判空和非空，例如 if(CollectionUtils.isNotEmpty())
            if (orderItemList.size() > 0) {
                // 已经用了StringBuffer类了，就不要再用字符串+号拼接了
                // sb.append("销售订单").append(li.getSalesOrderCode()).append("下还有未删除的清单，不允许删除").append("\n");
                sb.append("销售订单" + li.getSalesOrderCode() + "下还有未删除的清单，不允许删除" + "\n");
                salesOrderIds.remove(li.getSalesOrderId());
            }
        }
        this.removeByIds(salesOrderIds);

        // 参考
        // List<SalesOrder> salesOrders = this.listByIds(salesOrderIds);
        // if (CollectionUtils.isEmpty(salesOrders)) {
        //     return new StringBuffer("没有要删除的数据!");
        // }
        // // 返回错误信息
        // StringBuffer sb = new StringBuffer();
        // salesOrders.forEach(item -> {
        //     List<SalesOrderItem> orderItemList =
        //             this.salesOrderItemService.lambdaQuery()
        //                     .eq(SalesOrderItem::getSalesOrderId, item.getSalesOrderId())
        //                     .list();
        //     // 没数据后面就不操作了，直接到下个循环
        //     if (CollectionUtils.isEmpty(orderItemList)) {
        //         return;
        //     }
        //     sb.append("销售订单").append(item.getSalesOrderCode()).append("下还有未删除的清单，不允许删除").append("\n");
        //     salesOrderIds.remove(item.getSalesOrderId());
        // });
        // this.removeByIds(salesOrderIds);
        return sb;
    }

    // 根据主单id查找子项
    // -t3rik
    // 这个可以写成一行，提这个方法没有太大不要
    private List<SalesOrderItem> getItemList(SalesOrder salesOrder) {
        LambdaQueryWrapper<SalesOrderItem> queryWrapper = new LambdaQueryWrapper<>();
        queryWrapper.eq(SalesOrderItem::getSalesOrderId, salesOrder.getSalesOrderId());
        List<SalesOrderItem> orderItemList = salesOrderItemService.list(queryWrapper);
        return orderItemList;
    }

    @Override
    @Transactional
    public boolean refuse(SalesOrder salesOrder) {
        // 审批拒绝/提交
        this.updateById(salesOrder);
        List<SalesOrderItem> itemList = salesOrderItemService.lambdaQuery()
                .eq(SalesOrderItem::getSalesOrderId, salesOrder.getSalesOrderId())
                .list();
        // -t3rik
        // 参考
        // 这里如果itemList为空应该可以直接返回了。
        // if(CollectionUtils.isEmpty(itemList)){
        //     return false;
        // }
        // // 不为空再执行下面的
        // salesOrder.setSalesOrderItemList(itemList);
        // salesOrder.getSalesOrderItemList().forEach(object -> object.setStatus(salesOrder.getStatus()));
        // return salesOrderItemService.updateBatchById(salesOrder.getSalesOrderItemList());
        // 但是我不确定你的逻辑，如果SalesOrderItemList要是没有需要更新的子单，就算更新失败的话，那你直接返回false，不会触发事务
        // 要是你主单和子单都各自更新的话，返回false就没问题，
        // 要想触发事务，就得抛异常
        salesOrder.setSalesOrderItemList(itemList);
        if (salesOrder.getSalesOrderItemList().size() > 0) {
            salesOrder.getSalesOrderItemList().forEach(object -> object.setStatus(salesOrder.getStatus()));
        } else {
            return false;
        }
        return salesOrderItemService.updateBatchById(salesOrder.getSalesOrderItemList());




    }

    /**
     * 构建工单相应的bom列表
     */
    private List<ProWorkorderBom> buildWorkBomList(ProWorkorder workorder, List<ProClientOrderItem> clientOrderItems) {
        // 转化为workOrderBom列表
        return clientOrderItems.stream().map(orderItem -> {
            ProWorkorderBom workorderBom = new ProWorkorderBom();
            BeanUtil.copyProperties(orderItem, workorderBom);
            // 主表id
            workorderBom.setWorkorderId(workorder.getWorkorderId());
            // 规格型号
            workorderBom.setItemSpc(orderItem.getSpecification());
            // 默认物料
            workorderBom.setItemOrProduct(ItemTypeEnum.ITEM.getCode());
            return workorderBom;
        }).toList();
    }


    /**
     * 构建回写客户订单
     */
    private SalesOrderItem buildClientOrder(@NotNull SalesOrderItem salesOrderItem, @NotNull ProWorkorder workorder) {
        SalesOrderItem salesOrderItemUpdate = new SalesOrderItem();
        salesOrderItemUpdate.setSalesOrderItemId(salesOrderItem.getSalesOrderItemId());
        salesOrderItemUpdate.setWorkorderId(workorder.getWorkorderId());
        salesOrderItemUpdate.setWorkorderCode(workorder.getWorkorderCode());
        salesOrderItemUpdate.setWorkorderName(workorder.getWorkorderName());
        // 订单状态变为已生成生成订单
        salesOrderItemUpdate.setStatus(OrderStatusEnum.WORK_ORDER_FINISHED.getCode());
        return salesOrderItemUpdate;
    }


    /**
     * 构建生产订单
     */
    private @NotNull ProWorkorder buildWorkOrder(@NotNull SalesOrderItem salesOrderItem, String sourceCode) {
        ProWorkorder workorder = new ProWorkorder();
        BeanUtil.copyProperties(salesOrderItem, workorder);
        String workorderCode = this.autoCodeUtil.genSerialCode(UserConstants.WORKORDER_CODE, null);
        // 编码code
        workorder.setWorkorderCode(workorderCode);
        // 工单名称
        workorder.setWorkorderName(salesOrderItem.getProductName());
        // 工单类型 默认自产
        workorder.setWorkorderType(WorkOrderTypeEnum.SELF.getCode());
        // 订单来源  默认客户订单
        workorder.setOrderSource(WorkOrderSourceTypeEnum.ORDER.getCode());
        // 来源单据
        workorder.setSourceCode(sourceCode);
        // 订单状态 默认草稿状态
        workorder.setStatus(OrderStatusEnum.PREPARE.getCode());
        // 需求日期
        workorder.setRequestDate(salesOrderItem.getDeliveryDate());
        // 默认父订单
        workorder.setParentId(0L);
        // 默认父节点集合
        workorder.setAncestors("0");
        // 订货数量
        workorder.setQuantity(salesOrderItem.getSalesOrderQuantity());
        // 规格
        workorder.setProductSpc(salesOrderItem.getProductSpec());
        // 质量要求
        workorder.setRemark(salesOrderItem.getRemark());
        return workorder;
    }
}

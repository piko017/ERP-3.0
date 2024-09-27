package com.t3rik.mes.sales.service;

import java.util.List;

import com.baomidou.mybatisplus.extension.service.IService;
import com.t3rik.common.core.domain.AjaxResult;
import com.t3rik.mes.sales.domain.TranOrder;
import com.t3rik.mes.sales.domain.TranOrderLine;

/**
 * 销售送货单Service接口
 *
 * @author t3rik
 * @date 2024-09-09
 */
public interface ITranOrderService extends IService<TranOrder> {

     void saveTranOrder(TranOrder tranOrder);

    StringBuffer execute(TranOrder tranOrder) throws Exception;

    //update
    boolean updateTranOrder(TranOrder tranOrder);

    //remove
    StringBuffer deleteByIds(List<Long> tranOrderIds);

    //审批
    boolean refuse(TranOrder tranOrder);

}

package com.t3rik.mes.wm.domain;

import java.math.BigDecimal;

import com.t3rik.common.core.domain.BaseEntity;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.ToString;
import org.apache.commons.lang3.builder.ToStringBuilder;
import org.apache.commons.lang3.builder.ToStringStyle;
import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.annotation.TableId;
import com.t3rik.common.annotation.Excel;
import com.t3rik.common.enums.StatusEnum;

/**
* 生产废料单行对象 wm_waste_line
*
* @author t3rik
* @date 2024-05-11
*/
@TableName(value = "wm_waste_line")
@Data
@NoArgsConstructor
@AllArgsConstructor
public class WmWasteLine extends BaseEntity
{
    private static final long serialVersionUID = 1L;

    /** 行ID */
    @TableId
    private Long lineId;


    /** 废料单ID */
    @Excel(name = "废料单ID")
    private Long wasteId;


    /** 库存ID */
    @Excel(name = "库存ID")
    private Long materialStockId;


    /** 产品物料ID */
    @Excel(name = "产品物料ID")
    private Long itemId;


    /** 产品物料编码 */
    @Excel(name = "产品物料编码")
    private String itemCode;


    /** 产品物料名称 */
    @Excel(name = "产品物料名称")
    private String itemName;


    /** 规格型号 */
    @Excel(name = "规格型号")
    private String specification;


    /** 单位 */
    @Excel(name = "单位")
    private String unitOfMeasure;


    /** 废料数量 */
    @Excel(name = "废料数量")
    private BigDecimal quantityWaste;


    /** 领料批次号 */
    @Excel(name = "领料批次号")
    private String batchCode;


    /** 仓库ID */
    @Excel(name = "仓库ID")
    private Long warehouseId;


    /** 仓库编码 */
    @Excel(name = "仓库编码")
    private String warehouseCode;


    /** 仓库名称 */
    @Excel(name = "仓库名称")
    private String warehouseName;


    /** 库区ID */
    @Excel(name = "库区ID")
    private Long locationId;


    /** 库区编码 */
    @Excel(name = "库区编码")
    private String locationCode;


    /** 库区名称 */
    @Excel(name = "库区名称")
    private String locationName;


    /** 库位ID */
    @Excel(name = "库位ID")
    private Long areaId;


    /** 库位编码 */
    @Excel(name = "库位编码")
    private String areaCode;


    /** 库位名称 */
    @Excel(name = "库位名称")
    private String areaName;


    /** 预留字段1 */
    @Excel(name = "预留字段1")
    private String attr1;


    /** 预留字段2 */
    @Excel(name = "预留字段2")
    private String attr2;


    /** 预留字段3 */
    @Excel(name = "预留字段3")
    private Long attr3;


    /** 预留字段4 */
    @Excel(name = "预留字段4")
    private Long attr4;

    /** 更新人id */
    @Excel(name = "更新人id")
    private Long updateUserId;


    /** 创建人id */
    @Excel(name = "创建人id")
    private Long createUserId;

}

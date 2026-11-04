package com.insure.ai.common;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import lombok.Data;

import java.io.Serializable;

/**
 * 分页查询基础参数
 *
 * @author insurance-ai
 */
@Data
public class PageQuery implements Serializable {

    /** 当前页码,从 1 开始 */
    @Min(value = 1, message = "页码不能小于 1")
    private Long pageNum = Constants.DEFAULT_PAGE_NUM;

    /** 每页条数 */
    @Min(value = 1, message = "每页条数不能小于 1")
    @Max(value = 200, message = "每页条数不能超过 200")
    private Long pageSize = Constants.DEFAULT_PAGE_SIZE;
}

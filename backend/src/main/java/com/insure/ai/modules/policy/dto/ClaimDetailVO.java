package com.insure.ai.modules.policy.dto;

import com.insure.ai.modules.policy.entity.InsClaim;
import com.insure.ai.modules.policy.entity.InsClaimProgress;
import com.insure.ai.modules.policy.entity.InsPolicy;
import lombok.Data;

import java.io.Serializable;
import java.util.List;

/**
 * 理赔详情(案件 + 时间轴)
 *
 * @author insurance-ai
 */
@Data
public class ClaimDetailVO implements Serializable {

    /** 理赔案件 */
    private InsClaim claim;

    /** 关联保单(可能为空) */
    private InsPolicy policy;

    /** 进度时间轴,按发生时间正序 */
    private List<InsClaimProgress> progress;
}

package com.insure.ai.modules.system.service;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.insure.ai.common.BusinessException;
import com.insure.ai.common.Constants;
import com.insure.ai.common.PageResult;
import com.insure.ai.common.ResultCode;
import com.insure.ai.common.enums.CommonStatusEnum;
import com.insure.ai.modules.chat.mapper.ChatSessionMapper;
import com.insure.ai.modules.system.dto.CusUserQuery;
import com.insure.ai.modules.system.dto.CusUserVO;
import com.insure.ai.modules.system.entity.CusUser;
import com.insure.ai.modules.system.mapper.CusUserMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 后台客户管理服务(操作 cus_user 表,与管理员账号隔离)
 *
 * @author insurance-ai
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class CusUserService {

    private final CusUserMapper cusUserMapper;
    private final ChatSessionMapper chatSessionMapper;
    private final PasswordEncoder passwordEncoder;

    /**
     * 分页查询客户,并补充会话数量
     */
    public PageResult<CusUserVO> page(CusUserQuery query) {
        Page<CusUser> page = new Page<>(query.getPageNum(), query.getPageSize());
        cusUserMapper.selectPage(page, Wrappers.<CusUser>lambdaQuery()
                .eq(query.getStatus() != null, CusUser::getStatus, query.getStatus())
                .and(StringUtils.hasText(query.getKeyword()), wrapper -> wrapper
                        .like(CusUser::getNickname, query.getKeyword())
                        .or().like(CusUser::getRealName, query.getKeyword())
                        .or().like(CusUser::getPhone, query.getKeyword())
                        .or().like(CusUser::getUsername, query.getKeyword()))
                .orderByDesc(CusUser::getId));
        PageResult<CusUserVO> result = PageResult.of(page, CusUserVO::from);
        // 会话数量:一次分组查询后回填,避免 N+1
        Map<Long, Long> sessionMap = new HashMap<>();
        List<Map<String, Object>> rows = chatSessionMapper.countGroupByCusUser();
        rows.forEach(row -> sessionMap.put(toLong(row.get("cusUserId")), toLong(row.get("sessionCount"))));
        result.getRecords().forEach(item -> item.setSessionCount(sessionMap.getOrDefault(item.getId(), 0L)));
        return result;
    }

    /**
     * 客户详情
     */
    public CusUserVO detail(Long id) {
        CusUser user = cusUserMapper.selectById(id);
        if (user == null) {
            throw new BusinessException(ResultCode.CUS_USER_NOT_EXIST);
        }
        CusUserVO vo = CusUserVO.from(user);
        vo.setSessionCount(chatSessionMapper.selectCount(Wrappers.<com.insure.ai.modules.chat.entity.ChatSession>lambdaQuery()
                .eq(com.insure.ai.modules.chat.entity.ChatSession::getCusUserId, id)));
        return vo;
    }

    /**
     * 启用 / 禁用客户账号
     */
    @Transactional(rollbackFor = Exception.class)
    public void changeStatus(Long id, Integer status) {
        CusUser user = cusUserMapper.selectById(id);
        if (user == null) {
            throw new BusinessException(ResultCode.CUS_USER_NOT_EXIST);
        }
        CusUser update = new CusUser();
        update.setId(id);
        update.setStatus(status);
        cusUserMapper.updateById(update);
        log.info("[客户管理] 修改客户状态 id={} status={}", id, status);
    }

    /**
     * 重置客户密码为默认密码
     */
    @Transactional(rollbackFor = Exception.class)
    public void resetPassword(Long id) {
        CusUser user = cusUserMapper.selectById(id);
        if (user == null) {
            throw new BusinessException(ResultCode.CUS_USER_NOT_EXIST);
        }
        CusUser update = new CusUser();
        update.setId(id);
        update.setPassword(passwordEncoder.encode(Constants.DEFAULT_PASSWORD));
        cusUserMapper.updateById(update);
    }

    /**
     * 删除客户(逻辑删除,同时清空该客户的会话)
     */
    @Transactional(rollbackFor = Exception.class)
    public void delete(Long id) {
        CusUser user = cusUserMapper.selectById(id);
        if (user == null) {
            throw new BusinessException(ResultCode.CUS_USER_NOT_EXIST);
        }
        cusUserMapper.deleteById(id);
    }

    /**
     * 统计客户数量
     */
    public Long count() {
        return cusUserMapper.selectCount(null);
    }

    /**
     * 统计启用状态的客户数量
     */
    public Long countEnabled() {
        return cusUserMapper.selectCount(Wrappers.<CusUser>lambdaQuery()
                .eq(CusUser::getStatus, CommonStatusEnum.ENABLE.getCode()));
    }

    /**
     * 今日新增客户数量
     */
    public Long countToday() {
        return cusUserMapper.selectCount(Wrappers.<CusUser>lambdaQuery()
                .ge(CusUser::getCreateTime, java.time.LocalDate.now().atStartOfDay()));
    }

    /**
     * 数据库返回值转 Long
     */
    private Long toLong(Object value) {
        if (value == null) {
            return 0L;
        }
        return value instanceof Number number ? number.longValue() : Long.parseLong(value.toString());
    }
}

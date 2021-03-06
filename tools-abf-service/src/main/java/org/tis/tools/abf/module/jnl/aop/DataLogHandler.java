package org.tis.tools.abf.module.jnl.aop;

import com.alibaba.fastjson.JSON;
import com.baomidou.mybatisplus.enums.SqlMethod;
import com.baomidou.mybatisplus.mapper.Wrapper;
import org.apache.commons.lang3.StringUtils;
import org.aspectj.lang.JoinPoint;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.AfterReturning;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.annotation.Pointcut;
import org.springframework.stereotype.Component;
import org.springframework.util.CollectionUtils;
import org.tis.tools.abf.module.jnl.core.LogDataThreadLocal;
import org.tis.tools.abf.module.jnl.entity.enums.DataOperateType;
import org.tis.tools.abf.module.jnl.entity.vo.LogDataDetail;
import org.tis.tools.abf.module.jnl.util.DataUtils;
import org.tis.tools.abf.module.jnl.util.DiffUtils;

import java.io.Serializable;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;

/**
 * description:
 *
 * @author zhaoch
 * @date 2018/5/11
 **/
@Aspect
@Component
public class DataLogHandler {

    // TODO 目录调整后可能需要更改表达式
    @Pointcut("execution(public * org.tis.tools.abf.module.*.dao.*.insert*(..)) " +
            "&& !execution(public * org.tis.tools.abf.module.*.dao.jnl.insert*(..))" )
    public void insert() {
    }

    @Pointcut("execution(public * org.tis.tools.abf.module.*.dao.*.update*(..))" +
            "&& !execution(public * org.tis.tools.abf.module.*.dao.jnl.update*(..))" )
    public void update() {
    }

    @Pointcut("execution(public * org.tis.tools.abf.module.*.dao.*.delete*(..))" +
            "&& !execution(public * org.tis.tools.abf.module.*.dao.jnl.delete*(..))")
    public void delete() {
    }

    /**
     * 处理新增Mapper接口
     * @param pjp
     * @return
     * @throws Throwable
     */
    @AfterReturning(pointcut = "insert()", returning = "result", argNames = "pjp,result")
    public void insert(JoinPoint pjp, Integer result) throws Throwable {
        if (result > 0) {
            // 获取方法名
            String method = pjp.getSignature().getName();
            // insert 和 insertAllColumn 方法
            if (StringUtils.equals(method, SqlMethod.INSERT_ONE.getMethod()) ||
                    StringUtils.equals(method, SqlMethod.INSERT_ONE_ALL_COLUMN.getMethod())) {
                LogDataDetail data = new LogDataDetail();
                Object obj = pjp.getArgs()[0];
                data.setOperateType(DataOperateType.INSERT).setDataString(JSON.toJSONString(obj));
                collectDataInfo(data, obj);
                addDataLog(data);
            }
        }
    }

    /**
     * 处理删除Mapper接口
     * @param pjp
     * @throws Throwable
     */
    @Around("delete()")
    public Object delete(ProceedingJoinPoint pjp) throws Throwable {
        Object returnObj;
        // 获取方法名
        String method = pjp.getSignature().getName();
        // 目标类
        Class<?> targetClass = pjp.getTarget().getClass();
        // deleteById 方法
        if (StringUtils.equals(method, SqlMethod.DELETE_BY_ID.getMethod())) {
            // 获取删除前数据
            String id = (String) pjp.getArgs()[0];
            Object oldObj = targetClass
                    .getDeclaredMethod(SqlMethod.SELECT_BY_ID.getMethod(), Serializable.class)
                    .invoke(pjp.getTarget(), id);
            // 执行处理逻辑
            returnObj = pjp.proceed();
            if (((Integer) returnObj) > 0) {
                LogDataDetail data = new LogDataDetail();
                data.setOperateType(DataOperateType.DELETE);
                collectDataInfo(data, oldObj);
                addDataLog(data);
            }
        }
        // deleteBatchIds 方法-【List<T> selectBatchIds(@Param("coll") Collection<? extends Serializable> idList)】
        // 和deleteByMap 方法-【List<T> selectByMap(@Param("cm") Map<String, Object> columnMap)】
        // 和delete 方法-【Integer delete(@Param("ew") Wrapper<T> wrapper)】
        else if (StringUtils.equals(method, SqlMethod.DELETE_BATCH_BY_IDS.getMethod()) ||
                StringUtils.equals(method, SqlMethod.DELETE_BY_MAP.getMethod()) ||
                StringUtils.equals(method, SqlMethod.DELETE.getMethod())) {
            // 获取删除前数据
            List oldObjs;
            if (StringUtils.equals(method, SqlMethod.DELETE_BATCH_BY_IDS.getMethod())) {
                List idList = (List) pjp.getArgs()[0];
                oldObjs = (List) targetClass
                        .getDeclaredMethod(SqlMethod.SELECT_BATCH_BY_IDS.getMethod(), Collection.class)
                        .invoke(pjp.getTarget(), idList);
            } else if (StringUtils.equals(method, SqlMethod.DELETE_BY_MAP.getMethod())){
                Map columnMap = (Map) pjp.getArgs()[0];
                oldObjs = (List) targetClass
                        .getDeclaredMethod(SqlMethod.SELECT_BY_MAP.getMethod(), Map.class)
                        .invoke(pjp.getTarget(), columnMap);
            } else {
                Wrapper wrapper = (Wrapper) pjp.getArgs()[0];
                oldObjs = ((List) targetClass
                        .getDeclaredMethod(SqlMethod.SELECT_LIST.getMethod(), Wrapper.class)
                        .invoke(pjp.getTarget(), wrapper));
            }
            // 执行处理逻辑
            returnObj = pjp.proceed();
            if (((Integer) returnObj) > 0) {
                for (Object oldObj : oldObjs) {
                    LogDataDetail data = new LogDataDetail();
                    data.setOperateType(DataOperateType.DELETE);
                    collectDataInfo(data, oldObj);
                    addDataLog(data);
                }
            }
        } else {
            // 执行处理逻辑
            returnObj = pjp.proceed();
        }
        return returnObj;
    }

    /**
     * 处理修改Mapper接口
     * @param pjp
     * @return
     * @throws Throwable
     */
    @Around("update()")
    public Object update(ProceedingJoinPoint pjp) throws Throwable {
        Object returnObj = null;
        // 获取方法名
        String method = pjp.getSignature().getName();
        // 目标类
        Class<?> targetClass = pjp.getTarget().getClass();
        // updateById 和 updateAllColumnById 方法
        if (StringUtils.equals(method, SqlMethod.UPDATE_BY_ID.getMethod())
                || StringUtils.equals(method, SqlMethod.UPDATE_ALL_COLUMN_BY_ID.getMethod())) {
            LogDataDetail data = new LogDataDetail();
            data.setOperateType(DataOperateType.UPDATE);
            Object obj = pjp.getArgs()[0];
            collectDataInfo(data, obj);
            // 获取修改前数据
            Object oldObj = targetClass
                    .getDeclaredMethod(SqlMethod.SELECT_BY_ID.getMethod(), Serializable.class)
                    .invoke(pjp.getTarget(), data.getInstance().getDataGuid());
            // 执行处理逻辑
            returnObj = pjp.proceed();
            // 处理差异
            if (((Integer) returnObj) > 0) {
                if (StringUtils.equals(method, SqlMethod.UPDATE_BY_ID.getMethod())) {
                    data.setChanges(DiffUtils.getChangeItems(oldObj, obj, true));
                } else {
                    data.setChanges(DiffUtils.getChangeItems(oldObj, obj, false));
                }
            }
            addDataLog(data);
        }

        // update 方法-【Integer update(@Param("et") T entity, @Param("ew") Wrapper<T> wrapper)】
        else if (StringUtils.equals(method, SqlMethod.UPDATE.getMethod())) {
            Wrapper wrapper = (Wrapper) pjp.getArgs()[1];
            Object obj = pjp.getArgs()[0];
            // 获取修改前数据
            List oldObjs = (List) targetClass
                    .getDeclaredMethod(SqlMethod.SELECT_LIST.getMethod(), Wrapper.class)
                    .invoke(pjp.getTarget(), wrapper);
            // 执行处理逻辑
            returnObj = pjp.proceed();
            // 处理差异
            if (((Integer) returnObj) > 0) {
                for (Object oldObj : oldObjs) {
                    LogDataDetail data = new LogDataDetail();
                    data.setOperateType(DataOperateType.UPDATE);
                    collectDataInfo(data, oldObj);
                    data.setChanges(DiffUtils.getChangeItems(oldObj, obj, true));
                    addDataLog(data);
                }
            } else {
                // 执行处理逻辑
                returnObj = pjp.proceed();
            }
        }
        return returnObj;
    }

    private void collectDataInfo(LogDataDetail data, Object obj) {
        data.setDataClass(obj.getClass().getName())
                .setDataName(DataUtils.getEntityName(obj.getClass()))
                .setDataGuid(DataUtils.getEntityId(obj));
    }

    private void addDataLog(LogDataDetail data) {
        List<LogDataDetail> list = LogDataThreadLocal.getLogDataLocal();
        if (CollectionUtils.isEmpty(list)) {
            List<LogDataDetail> dataDetails = new ArrayList<>();
            dataDetails.add(data);
            LogDataThreadLocal.setLogDataLocal(dataDetails);
        } else {
            list.add(data);
        }
    }


}

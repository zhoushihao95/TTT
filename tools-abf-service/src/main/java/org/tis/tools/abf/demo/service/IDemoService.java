package org.tis.tools.abf.demo.service;

import com.baomidou.mybatisplus.service.IService;
import org.tis.tools.abf.demo.entity.Demo;
import org.tis.tools.abf.demo.vo.DemoTreeVo;

import java.util.List;

/**
 * Demo 的业务逻辑接口
 *
 * @author Tools Modeler
 * @since 2018-03-01 12:12:34 123
 */
public interface IDemoService extends IService<Demo> {

    /**
     * 获取Demo数据的树节点信息
     *
     * @return
     */
    List<DemoTreeVo> tree();

    /**
     * 新增记录
     * @param demo
     */
    void add(Demo demo) ;

    /**
     * 获取历史数据节点信息
     * @return
     */
    List<DemoTreeVo> treeHis();

    /**
     * 新增历史记录
     * @param demo
     */
    void addHis(Demo demo) ;
}
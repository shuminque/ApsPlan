package com.depository_manage.config;

import com.alibaba.druid.spring.boot.autoconfigure.DruidDataSourceBuilder;
import com.baomidou.mybatisplus.extension.spring.MybatisSqlSessionFactoryBean;
import com.baomidou.mybatisplus.extension.plugins.MybatisPlusInterceptor;
import com.baomidou.mybatisplus.extension.plugins.inner.PaginationInnerInterceptor;
import com.baomidou.mybatisplus.annotation.DbType;
import org.apache.ibatis.session.SqlSessionFactory;
import org.mybatis.spring.annotation.MapperScan;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;

import javax.sql.DataSource;

@Configuration
@MapperScan(
        basePackages = "com.depository_manage.mapper.aps",
        sqlSessionFactoryRef = "apsSqlSessionFactory"
)
public class ApsPlanDataSourceConfig {

    @Bean(name = "apsDataSource")
    @ConfigurationProperties(prefix = "spring.datasource.apsplan")
    public DataSource apsDataSource() {
        return DruidDataSourceBuilder.create().build();
    }

    @Bean(name = "apsSqlSessionFactory")
    public SqlSessionFactory apsSqlSessionFactory(
            @Qualifier("apsDataSource") DataSource dataSource) throws Exception {

        MybatisSqlSessionFactoryBean bean = new MybatisSqlSessionFactoryBean();
        bean.setDataSource(dataSource);
        // 可选：MP 全局配置、插件等
        return bean.getObject();
    }

    @Bean(name = "apsTransactionManager")
    public DataSourceTransactionManager apsTransactionManager(
            @Qualifier("apsDataSource") DataSource dataSource) {
        return new DataSourceTransactionManager(dataSource);
    }

    // 分页插件
    @Bean
    public MybatisPlusInterceptor mybatisPlusInterceptor() {
        MybatisPlusInterceptor interceptor = new MybatisPlusInterceptor();
        interceptor.addInnerInterceptor(new PaginationInnerInterceptor(DbType.MYSQL));
        return interceptor;
    }
}

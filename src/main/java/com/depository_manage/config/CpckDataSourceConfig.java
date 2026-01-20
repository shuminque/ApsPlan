package com.depository_manage.config;

import com.alibaba.druid.spring.boot.autoconfigure.DruidDataSourceBuilder;
import org.apache.ibatis.session.SqlSessionFactory;
import org.mybatis.spring.SqlSessionFactoryBean;
import org.mybatis.spring.annotation.MapperScan;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;

import javax.sql.DataSource;

@Configuration
@MapperScan(
        basePackages = "com.depository_manage.mapper.cpck",
        sqlSessionFactoryRef = "cpckSqlSessionFactory"
)
public class CpckDataSourceConfig {

    @Primary
    @Bean(name = "cpckDataSource")
    @ConfigurationProperties(prefix = "spring.datasource.cpck")
    public DataSource cpckDataSource() {
        return DruidDataSourceBuilder.create().build();
    }

    @Primary
    @Bean(name = "cpckSqlSessionFactory")
    public SqlSessionFactory cpckSqlSessionFactory(
            @Qualifier("cpckDataSource") DataSource dataSource) throws Exception {

        SqlSessionFactoryBean bean = new SqlSessionFactoryBean();
        bean.setDataSource(dataSource);
        return bean.getObject();
    }

    @Primary
    @Bean(name = "cpckTransactionManager")
    public DataSourceTransactionManager cpckTransactionManager(
            @Qualifier("cpckDataSource") DataSource dataSource) {
        return new DataSourceTransactionManager(dataSource);
    }
}

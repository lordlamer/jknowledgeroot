package org.knowledgeroot.app.config;

import ma.glasnost.orika.Converter;
import ma.glasnost.orika.Mapper;
import ma.glasnost.orika.MapperFactory;
import ma.glasnost.orika.converter.ConverterFactory;
import ma.glasnost.orika.impl.ConfigurableMapper;
import ma.glasnost.orika.impl.DefaultMapperFactory;
import org.knowledgeroot.app.content.mapper.ContentToContentDtoMapper;
import org.knowledgeroot.app.content.mapper.ContentToContentEntityMapper;
import org.knowledgeroot.app.file.mapper.FileToFileDtoMapper;
import org.knowledgeroot.app.file.mapper.FileToFileEntityMapper;
import org.knowledgeroot.app.group.mapper.GroupToGroupDtoMapper;
import org.knowledgeroot.app.group.mapper.GroupToGroupEntityMapper;
import org.knowledgeroot.app.page.mapper.PageToPageDtoMapper;
import org.knowledgeroot.app.page.mapper.PageToPageEntityMapper;
import org.knowledgeroot.app.user.mapper.UserToUserDtoMapper;
import org.knowledgeroot.app.user.mapper.UserToUserEntityMapper;
import org.springframework.beans.BeansException;
import org.springframework.context.ApplicationContext;
import org.springframework.context.ApplicationContextAware;
import org.springframework.stereotype.Component;

import java.util.Map;

@Component
public class OrikaMapper extends ConfigurableMapper implements ApplicationContextAware {

    private MapperFactory mapperFactory;
    private ApplicationContext applicationContext;

    public OrikaMapper() {
        super(false);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    protected void configure(MapperFactory factory) {
        this.mapperFactory = factory;

        addAllSpringBeans(applicationContext);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    protected void configureFactoryBuilder(final DefaultMapperFactory.Builder factoryBuilder) {
        // Nothing to configure for now
    }

    /**
     * Constructs and registers a {@link Mapper} into the {@link MapperFactory} using a {@link Mapper}.
     *
     * @param mapper
     */
    @SuppressWarnings("rawtypes")
    public void addMapper(Mapper<?, ?> mapper) {
        if(mapper instanceof UserToUserDtoMapper) {
            mapperFactory.classMap(mapper.getAType(), mapper.getBType())
                    //.exclude("password")
                    .byDefault()
                    .customize((Mapper) mapper)
                    .register();
        } else if(mapper instanceof UserToUserEntityMapper) {
            mapperFactory.classMap(mapper.getAType(), mapper.getBType())
                    .byDefault()
                    .customize((Mapper) mapper)
                    .register();
        } else if(mapper instanceof PageToPageDtoMapper) {
            mapperFactory.classMap(mapper.getAType(), mapper.getBType())
                    .byDefault()
                    .customize((Mapper) mapper)
                    .register();
        } else if(mapper instanceof PageToPageEntityMapper) {
            mapperFactory.classMap(mapper.getAType(), mapper.getBType())
                    .byDefault()
                    .customize((Mapper) mapper)
                    .register();
        } else if(mapper instanceof GroupToGroupDtoMapper) {
            mapperFactory.classMap(mapper.getAType(), mapper.getBType())
                    .byDefault()
                    .customize((Mapper) mapper)
                    .register();
        } else if(mapper instanceof GroupToGroupEntityMapper) {
            mapperFactory.classMap(mapper.getAType(), mapper.getBType())
                    .byDefault()
                    .customize((Mapper) mapper)
                    .register();
        } else if(mapper instanceof FileToFileDtoMapper) {
            mapperFactory.classMap(mapper.getAType(), mapper.getBType())
                    .byDefault()
                    .customize((Mapper) mapper)
                    .register();
        } else if(mapper instanceof FileToFileEntityMapper) {
            mapperFactory.classMap(mapper.getAType(), mapper.getBType())
                    .byDefault()
                    .customize((Mapper) mapper)
                    .register();
        } else if(mapper instanceof ContentToContentDtoMapper) {
            mapperFactory.classMap(mapper.getAType(), mapper.getBType())
                    .byDefault()
                    .customize((Mapper) mapper)
                    .register();
        } else if(mapper instanceof ContentToContentEntityMapper) {
            mapperFactory.classMap(mapper.getAType(), mapper.getBType())
                    .byDefault()
                    .customize((Mapper) mapper)
                    .register();
        } else {
            mapperFactory.classMap(mapper.getAType(), mapper.getBType())
                    .byDefault()
                    .mapNulls(true)
                    .customize((Mapper) mapper)
                    .register();
        }
    }

    /**
     * Registers a {@link Converter} into the {@link ConverterFactory}.
     *
     * @param converter
     */
    public void addConverter(Converter<?, ?> converter) {
        mapperFactory.getConverterFactory().registerConverter(converter);
    }


    /**
     * Scans the appliaction context and registers all Mappers and Converters found in it.
     *
     * @param applicationContext
     */
    @SuppressWarnings("rawtypes")
    private void addAllSpringBeans(final ApplicationContext applicationContext) {
        Map<String, Mapper> mappers = applicationContext.getBeansOfType(Mapper.class);

        for (Mapper mapper : mappers.values()) {
            addMapper(mapper);
        }

        Map<String, Converter> converters = applicationContext.getBeansOfType(Converter.class);

        for (Converter converter : converters.values()) {
            addConverter(converter);
        }

        //Map<String, ConfigurableMapper> configurations = applicationContext.getBeansOfType(ConfigurableMapper.class);
    }

    public void setApplicationContext(ApplicationContext applicationContext) throws BeansException {
        this.applicationContext = applicationContext;

        init();
    }

}

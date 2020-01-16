package io.micronaut.data.spring.jpa.intercept;

import edu.umd.cs.findbugs.annotations.NonNull;
import io.micronaut.aop.MethodInvocationContext;
import io.micronaut.core.annotation.Internal;
import io.micronaut.core.convert.ConversionService;
import io.micronaut.core.type.ReturnType;
import io.micronaut.data.intercept.RepositoryMethodKey;
import io.micronaut.data.jpa.operations.JpaRepositoryOperations;
import io.micronaut.data.operations.RepositoryOperations;
import io.micronaut.data.runtime.intercept.AbstractQueryInterceptor;
import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.data.jpa.domain.Specification;

import javax.persistence.EntityManager;
import javax.persistence.NoResultException;
import javax.persistence.TypedQuery;
import javax.persistence.criteria.CriteriaBuilder;
import javax.persistence.criteria.CriteriaQuery;
import javax.persistence.criteria.Predicate;
import javax.persistence.criteria.Root;

/**
 * Implementation of {@code findOne(Specification)} for Spring Data JPA specifications.
 *
 * @author graemerocher
 * @since 1.0
 */
@Internal
public class FindOneSpecificationInterceptor extends AbstractQueryInterceptor<Object, Object> {
    private final JpaRepositoryOperations jpaOperations;

    /**
     * Default constructor.
     *
     * @param operations The operations
     */
    protected FindOneSpecificationInterceptor(@NonNull RepositoryOperations operations) {
        super(operations);
        if (operations instanceof JpaRepositoryOperations) {
            this.jpaOperations = (JpaRepositoryOperations) operations;
        } else {
            throw new IllegalStateException("Repository operations must be na instance of JpaRepositoryOperations");
        }
    }

    @Override
    public Object intercept(RepositoryMethodKey methodKey, MethodInvocationContext<Object, Object> context) {
        final Object parameterValue = context.getParameterValues()[0];
        if (parameterValue instanceof Specification) {
            Specification specification = (Specification) parameterValue;
            final EntityManager entityManager = jpaOperations.getCurrentEntityManager();
            final CriteriaBuilder criteriaBuilder = entityManager.getCriteriaBuilder();
            final CriteriaQuery<Object> query = criteriaBuilder.createQuery((Class<Object>) getRequiredRootEntity(context));
            final Root<Object> root = query.from((Class<Object>) getRequiredRootEntity(context));
            final Predicate predicate = specification.toPredicate(root, query, criteriaBuilder);
            query.where(predicate);
            query.select(root);

            final TypedQuery<?> typedQuery = entityManager.createQuery(query);
            try {
                final Object result = typedQuery.getSingleResult();
                final ReturnType<?> rt = context.getReturnType();
                final Class<?> returnType = rt.getType();
                if (returnType.isInstance(result)) {
                    return result;
                } else {
                    return ConversionService.SHARED.convertRequired(
                            result,
                            rt.asArgument()
                    );
                }
            } catch (NoResultException e) {
                if (context.isNullable()) {
                    return null;
                } else {
                    throw new EmptyResultDataAccessException(1);
                }
            }
        } else {
            throw new IllegalArgumentException("Argument must be an instance of: " + Specification.class);
        }
    }
}

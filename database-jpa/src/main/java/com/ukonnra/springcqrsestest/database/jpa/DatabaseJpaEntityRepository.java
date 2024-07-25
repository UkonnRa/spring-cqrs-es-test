package com.ukonnra.springcqrsestest.database.jpa;

import com.ukonnra.springcqrsestest.shared.AbstractEntity;
import com.ukonnra.springcqrsestest.shared.EntityRepository;
import com.ukonnra.springcqrsestest.shared.Event;
import com.ukonnra.springcqrsestest.shared.Query;

public interface DatabaseJpaEntityRepository<
        E extends AbstractEntity<V>,
        Q extends Query,
        V extends Event,
        O extends AbstractEntityPO<E>>
    extends EntityRepository<E, Q, V> {
  default E convertToEntity(final O po) {
    final var entity = this.getDefaultEntity(po.getId());
    entity.setCreatedDate(po.getCreatedDate());
    entity.setVersion(po.getVersion());
    entity.setDeletedDate(po.getDeletedDate());
    return entity;
  }
}

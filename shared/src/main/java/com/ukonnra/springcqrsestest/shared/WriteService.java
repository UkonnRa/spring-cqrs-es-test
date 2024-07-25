package com.ukonnra.springcqrsestest.shared;

import com.ukonnra.springcqrsestest.shared.user.User;
import java.util.Set;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.lang.Nullable;
import org.springframework.transaction.annotation.Transactional;

public interface WriteService<
        E extends AbstractEntity<V>,
        C extends Command,
        V extends Event,
        Q extends Query,
        P extends Presentation,
        R extends EntityRepository<E, Q, V>>
    extends ReadService<E, Q, P> {
  Logger LOGGER = LoggerFactory.getLogger(WriteService.class);

  EventRepository getEventRepository();

  R getRepository();

  Set<Event> doHandleCommand(@Nullable final User user, final C command);

  @Transactional
  default void handleCommand(@Nullable final User user, final C command) {
    final var events = this.doHandleCommand(user, command);
    this.getEventRepository().saveAll(events);

    final var ids = events.stream().map(Event::id).collect(Collectors.toSet());
    this.getRepository().refreshSnapshots(ids);
  }

  @Override
  default Set<P> findAll(@Nullable final User operator, final Q query, @Nullable Integer size) {
    LOGGER.info("Find All: query: {}, size: {}", query, size);
    final var entities = this.getRepository().findAll(query, size);
    LOGGER.info("  Find from Repository: {}", entities);
    return this.convert(operator, entities);
  }
}

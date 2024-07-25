package com.ukonnra.springcqrsestest.shared.journal;

import com.ukonnra.springcqrsestest.shared.AbstractEntity;
import com.ukonnra.springcqrsestest.shared.Event;
import com.ukonnra.springcqrsestest.shared.EventRepository;
import com.ukonnra.springcqrsestest.shared.WriteService;
import com.ukonnra.springcqrsestest.shared.errors.NoPermissionError;
import com.ukonnra.springcqrsestest.shared.user.User;
import java.time.Instant;
import java.util.Collection;
import java.util.HashSet;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;
import lombok.AllArgsConstructor;
import lombok.Getter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.lang.Nullable;
import org.springframework.stereotype.Service;

public interface JournalService
    extends WriteService<
        Journal,
        JournalCommand,
        JournalEvent,
        JournalQuery,
        JournalPresentation,
        JournalRepository> {
  Logger LOGGER = LoggerFactory.getLogger(JournalService.class);

  @Override
  default Set<JournalPresentation> convert(
      @Nullable final User operator, final Collection<Journal> entities) {
    return entities.stream()
        .map(
            entity -> {
              LOGGER.info("For each: {}", entity);
              return JournalPresentation.of(operator, entity);
            })
        .filter(Objects::nonNull)
        .collect(Collectors.toSet());
  }

  @Override
  default Set<Event> doHandleCommand(@Nullable final User operator, final JournalCommand command) {
    return switch (command) {
      case JournalCommand.Batch batch -> this.batch(operator, batch);
      case JournalCommand.Create create -> this.create(operator, Set.of(create));
      case JournalCommand.Delete(UUID id) -> this.delete(operator, Set.of(id));
      case JournalCommand.Update update -> this.update(operator, Set.of(update));
    };
  }

  private Set<Event> create(
      @Nullable final User operator, final Set<JournalCommand.Create> commands) {

    final var events = new HashSet<Event>();
    for (final var command : commands) {
      if (operator == null) {
        throw new NoPermissionError(Journal.TYPE, Map.of(Journal.FIELD_NAME, command.name()));
      }

      events.add(new JournalEvent.Created(command));
    }
    return events;
  }

  private Set<Event> update(
      @Nullable final User operator, final Set<JournalCommand.Update> commands) {
    final var ids = commands.stream().map(JournalCommand.Update::id).collect(Collectors.toSet());
    final var models =
        this.getRepository().findAllByIds(ids).stream()
            .collect(Collectors.toMap(AbstractEntity::getId, Function.identity()));

    return commands.stream()
        .map(
            command -> {
              final var model = models.get(command.id());
              if (model == null || command.empty()) {
                return null;
              }

              final var permission = model.getPermission(operator);
              if (permission == null) {
                throw new NoPermissionError(Journal.TYPE, model.getId());
              }

              if (!command.name().isEmpty() && !permission.isWriteable(Journal.FIELD_NAME)) {
                throw new NoPermissionError(User.TYPE, model.getId(), Set.of(Journal.FIELD_NAME));
              }

              if (!command.admins().isEmpty() && !permission.isWriteable(Journal.FIELD_ADMINS)) {
                throw new NoPermissionError(User.TYPE, model.getId(), Set.of(Journal.FIELD_ADMINS));
              }

              if (command.members() != null
                  && !command.members().isEmpty()
                  && !permission.isWriteable(Journal.FIELD_MEMBERS)) {
                throw new NoPermissionError(
                    User.TYPE, model.getId(), Set.of(Journal.FIELD_MEMBERS));
              }

              if (command.tags() != null
                  && !command.tags().isEmpty()
                  && !permission.isWriteable(Journal.FIELD_TAGS)) {
                throw new NoPermissionError(User.TYPE, model.getId(), Set.of(Journal.FIELD_TAGS));
              }

              final var event =
                  new JournalEvent.Updated(
                      model.getId(),
                      model.getVersion() + 1,
                      command.name(),
                      command.admins(),
                      command.members(),
                      command.tags());
              model.handleEvent(event);

              return event;
            })
        .filter(Objects::nonNull)
        .collect(Collectors.toSet());
  }

  private Set<Event> delete(@Nullable final User operator, final Collection<UUID> ids) {
    final var models = this.getRepository().findAllByIds(ids);
    final var now = Instant.now();
    return models.stream()
        .map(
            model -> {
              final var permission = model.getPermission(operator);
              if (permission == null || !permission.isWriteable()) {
                throw new NoPermissionError(Journal.TYPE, model.getId());
              }

              return new JournalEvent.Deleted(model.getId(), model.getVersion() + 1, now);
            })
        .collect(Collectors.toSet());
  }

  private Set<Event> batch(@Nullable final User operator, final JournalCommand.Batch command) {
    final var events = new HashSet<Event>();

    events.addAll(this.create(operator, command.create()));
    events.addAll(this.delete(operator, command.delete()));
    events.addAll(
        this.update(
            operator,
            command.update().stream()
                .filter(c -> !command.delete().contains(c.id()))
                .collect(Collectors.toSet())));

    return events;
  }

  @Service
  @AllArgsConstructor
  @Getter
  class Impl implements JournalService {
    private final EventRepository eventRepository;
    private final JournalRepository repository;
  }
}

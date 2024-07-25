package com.ukonnra.springcqrsestest.database.jpa.journal;

import com.ukonnra.springcqrsestest.database.jpa.DatabaseJpaEntityRepository;
import com.ukonnra.springcqrsestest.database.jpa.user.UserPO;
import com.ukonnra.springcqrsestest.database.jpa.user.UserPORepository;
import com.ukonnra.springcqrsestest.shared.EventRepository;
import com.ukonnra.springcqrsestest.shared.journal.Journal;
import com.ukonnra.springcqrsestest.shared.journal.JournalEvent;
import com.ukonnra.springcqrsestest.shared.journal.JournalQuery;
import com.ukonnra.springcqrsestest.shared.journal.JournalRepository;
import java.util.Collection;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Pageable;
import org.springframework.lang.Nullable;
import org.springframework.stereotype.Service;

@Service
@AllArgsConstructor
@Getter
@Slf4j
public class JournalRepositoryImpl
    implements JournalRepository,
        DatabaseJpaEntityRepository<Journal, JournalQuery, JournalEvent, JournalPO> {
  private final EventRepository eventRepository;
  private final JournalPORepository journalPORepository;
  private final UserPORepository userPORepository;

  @Override
  public Set<Journal> findAll(@Nullable JournalQuery query, @Nullable Integer size) {
    final List<JournalPO> pos;

    final var specification = new JournalSpecification(query);
    final var page = size != null && size > 0 ? Pageable.ofSize(size) : null;

    if (page != null) {
      pos = this.journalPORepository.findAll(specification, page).getContent();
    } else {
      pos = this.journalPORepository.findAll(specification);
    }

    return pos.stream().map(this::convertToEntity).collect(Collectors.toSet());
  }

  @Override
  public Set<Journal> findAllByIds(Collection<UUID> ids) {
    return this.journalPORepository.findAllById(ids).stream()
        .map(this::convertToEntity)
        .collect(Collectors.toSet());
  }

  @Override
  public void saveAll(Collection<Journal> entities) {
    final var userIds =
        entities.stream()
            .flatMap(model -> Stream.of(model.getAdmins(), model.getMembers()))
            .flatMap(Set::stream)
            .collect(Collectors.toSet());
    final var users =
        this.userPORepository.findAllById(userIds).stream()
            .collect(Collectors.toMap(UserPO::getId, Function.identity()));
    final var pos =
        entities.stream().map(entity -> new JournalPO(entity, users)).collect(Collectors.toSet());
    this.journalPORepository.saveAllAndFlush(pos);
  }

  @Override
  public Journal convertToEntity(JournalPO po) {
    final var entity = DatabaseJpaEntityRepository.super.convertToEntity(po);
    entity.setName(po.getName());
    entity.setAdmins(po.getAdminIds());
    entity.setMembers(po.getMemberIds());
    entity.setTags(po.getTags());
    return entity;
  }
}

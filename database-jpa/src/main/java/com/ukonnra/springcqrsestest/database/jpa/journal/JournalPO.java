package com.ukonnra.springcqrsestest.database.jpa.journal;

import com.ukonnra.springcqrsestest.database.jpa.AbstractEntityPO;
import com.ukonnra.springcqrsestest.database.jpa.user.UserPO;
import com.ukonnra.springcqrsestest.shared.AbstractEntity;
import com.ukonnra.springcqrsestest.shared.journal.Journal;
import jakarta.persistence.CascadeType;
import jakarta.persistence.CollectionTable;
import jakarta.persistence.Column;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Index;
import jakarta.persistence.OneToMany;
import jakarta.persistence.Table;
import jakarta.validation.constraints.Size;
import java.util.Collection;
import java.util.HashSet;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.ToString;
import lombok.extern.slf4j.Slf4j;

@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@ToString(callSuper = true)
@Entity(name = Journal.TYPE)
@Table(
    name = Journal.TYPE,
    indexes = {
      @Index(columnList = "deletedDate"),
      @Index(columnList = "name"),
    })
@Slf4j
public class JournalPO extends AbstractEntityPO<Journal> {

  @Size(min = AbstractEntity.MIN_NAMELY, max = AbstractEntity.MAX_NAMELY)
  @Column(nullable = false)
  private String name;

  @OneToMany(mappedBy = "journal", cascade = CascadeType.ALL, fetch = FetchType.EAGER)
  private Set<JournalUserPO> journalUsers = new HashSet<>();

  @ElementCollection(fetch = FetchType.EAGER)
  @CollectionTable(
      name = "journal_tags",
      indexes = @Index(unique = true, columnList = "journal_id, tag"))
  @Size(max = AbstractEntity.MAX_TAGS)
  @Column(name = "tag", nullable = false, length = AbstractEntity.MAX_NAMELY)
  private Set<@Size(min = AbstractEntity.MIN_NAMELY, max = AbstractEntity.MAX_NAMELY) String> tags =
      new HashSet<>();

  public JournalPO(final Journal entity, final Map<UUID, UserPO> users) {
    super(entity);
    this.name = entity.getName();

    final var admins =
        entity.getAdmins().stream()
            .map(users::get)
            .filter(Objects::nonNull)
            .collect(Collectors.toSet());
    this.setAdmins(admins);

    final var members =
        entity.getMembers().stream()
            .map(users::get)
            .filter(Objects::nonNull)
            .collect(Collectors.toSet());
    this.setMembers(members);

    this.tags = entity.getTags();
  }

  public Set<JournalUserPO> getAdmins() {
    return this.journalUsers.stream().filter(JournalUserPO::getAdmin).collect(Collectors.toSet());
  }

  public Set<UUID> getAdminIds() {
    return this.getAdmins().stream().map(po -> po.getUser().getId()).collect(Collectors.toSet());
  }

  public void setAdmins(final Collection<UserPO> admins) {
    this.journalUsers =
        Stream.concat(JournalUserPO.ofAll(this, admins, true).stream(), this.getMembers().stream())
            .collect(Collectors.toSet());
  }

  public Set<JournalUserPO> getMembers() {
    return this.journalUsers.stream().filter(po -> !po.getAdmin()).collect(Collectors.toSet());
  }

  public Set<UUID> getMemberIds() {
    return this.getMembers().stream().map(po -> po.getUser().getId()).collect(Collectors.toSet());
  }

  public void setMembers(final Collection<UserPO> members) {
    this.journalUsers =
        Stream.concat(JournalUserPO.ofAll(this, members, true).stream(), this.getAdmins().stream())
            .collect(Collectors.toSet());
  }
}

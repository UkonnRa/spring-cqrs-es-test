package com.ukonnra.springcqrsestest.database.jpa.journal;

import com.ukonnra.springcqrsestest.database.jpa.user.UserPO;
import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import jakarta.persistence.EmbeddedId;
import jakarta.persistence.Entity;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.MapsId;
import jakarta.persistence.Table;
import java.util.Collection;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.ToString;

@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@ToString(callSuper = true)
@Entity(name = JournalUserPO.TYPE)
@Table(name = JournalUserPO.TYPE)
public class JournalUserPO {
  public static final String TYPE = "journal_users";

  @EmbeddedId private Id id;

  @ManyToOne(optional = false)
  @MapsId("journalId")
  @JoinColumn(name = "journal_id")
  @ToString.Exclude
  private JournalPO journal;

  @ManyToOne(optional = false)
  @MapsId("userId")
  @JoinColumn(name = "user_id")
  @ToString.Exclude
  private UserPO user;

  private boolean admin;

  public static Set<JournalUserPO> ofAll(
      final JournalPO journal, final Collection<UserPO> users, boolean admin) {
    return users.stream()
        .map(user -> new JournalUserPO(journal, user, admin))
        .collect(Collectors.toSet());
  }

  public JournalUserPO(final JournalPO journal, final UserPO user, boolean admin) {
    this.id = new Id(journal.getId(), user.getId());
    this.journal = journal;
    this.user = user;
    this.admin = admin;
  }

  @Embeddable
  public record Id(
      @Column(name = "journal_id", nullable = false) UUID journalId,
      @Column(name = "user_id", nullable = false) UUID userId) {}
}

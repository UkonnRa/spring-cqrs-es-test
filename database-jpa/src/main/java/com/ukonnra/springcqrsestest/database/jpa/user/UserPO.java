package com.ukonnra.springcqrsestest.database.jpa.user;

import com.ukonnra.springcqrsestest.database.jpa.AbstractEntityPO;
import com.ukonnra.springcqrsestest.shared.AbstractEntity;
import com.ukonnra.springcqrsestest.shared.user.User;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import jakarta.validation.constraints.Size;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.ToString;

@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@ToString(callSuper = true)
@Entity(name = User.TYPE)
@Table(
    name = User.TYPE,
    indexes = {
      @Index(columnList = "deletedDate"),
      @Index(columnList = "loginName", unique = true),
      @Index(columnList = "displayName"),
      @Index(columnList = "systemAdmin"),
    })
public class UserPO extends AbstractEntityPO<User> {
  @Size(min = AbstractEntity.MIN_NAMELY, max = AbstractEntity.MAX_NAMELY)
  @Column(nullable = false)
  private String loginName;

  @Size(min = AbstractEntity.MIN_NAMELY, max = AbstractEntity.MAX_NAMELY)
  @Column(nullable = false)
  private String displayName;

  @Column(nullable = false)
  private boolean systemAdmin;

  public UserPO(final User user) {
    super(user);
    this.loginName = user.getLoginName();
    this.displayName = user.getDisplayName();
    this.systemAdmin = user.getSystemAdmin();
  }
}

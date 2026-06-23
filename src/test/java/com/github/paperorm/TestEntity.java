package com.github.paperorm;

import com.github.paperorm.annotation.Column;
import com.github.paperorm.annotation.Entity;
import com.github.paperorm.annotation.Id;
import com.github.paperorm.annotation.Table;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "test_entities")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public final class TestEntity {

  @Id(autoIncrement = true)
  @Column(nullable = false)
  private Long id;

  @Column(nullable = false, length = 64)
  private String name;

  @Column(nullable = false)
  private int count;

  @Column private Boolean active;
}

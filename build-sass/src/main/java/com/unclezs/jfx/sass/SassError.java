package com.unclezs.jfx.sass;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * sass语法错误提示
 *
 * @author blog.unclezs.com
 * @date 2021/3/29 22:52
 */
@Getter
@Setter
@NoArgsConstructor
public class SassError {
  private int status;
  private String file;
  private int line;
  private int column;
  private String message;
  private String formatted;
}

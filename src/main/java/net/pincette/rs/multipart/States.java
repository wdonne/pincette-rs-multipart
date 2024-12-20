package net.pincette.rs.multipart;

/**
 * @author Werner Donné
 */
enum States {
  BODY,
  CLOSE,
  DELIMITER,
  EPILOGUE,
  HEADERS,
  PREAMBLE
}

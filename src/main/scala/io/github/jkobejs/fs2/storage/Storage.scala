package io.github.jkobejs.fs2.storage

import fs2.{Pipe, Stream}

trait Storage[F[_]] {
  type Path
  type StorageObject

  def list(path: Path): Stream[F, StorageObject]

  def get(path: Path): F[StorageObject]

  def download(path: Path): Stream[F, Byte]

  def put(path: Path): Pipe[F, Byte, Unit]

  def move(source: Path, destination: Path): F[Unit]

  def remove(path: Path): F[Unit]
}

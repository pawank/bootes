package com.bootes.config

import com.bootes.migration.MigrationConfig
import com.typesafe.config.Config
import zio.config._
import zio.config.syntax._
import zio.config.magnolia._
import zio.config.typesafe.TypesafeConfig
import zio.{Has, Layer, ULayer, ZIO, system}
import zio.system.System

import java.io.File

object Configuration {
  case class AppConfig(migration: MigrationConfig)

  implicit val appConfigDescriptor: ConfigDescriptor[AppConfig] = descriptor[AppConfig].mapKey(toKebabCase)

  val live: ULayer[Has[AppConfig]] =
    TypesafeConfig.fromDefaultLoader(appConfigDescriptor).orDie

  val migrationConfigLive: ULayer[Has[MigrationConfig]] =
    TypesafeConfig.fromDefaultLoader(appConfigDescriptor).narrow(_.migration).orDie

  case class KeycloakConfigValues(url: String, masterRealm: String, realm: Option[String])
  case class KeycloakConfig(keycloak: KeycloakConfigValues)

  val keycloakConfigAutomatic = descriptor[KeycloakConfig]

  val keycloakConfigLayer: Layer[ReadError[String], Has[KeycloakConfig]] = System.live >>> ZConfig.fromSystemEnv(keycloakConfigAutomatic)

  import zio.config.typesafe._
  val keycloakConfigDescription: ZIO[system.System, ReadError[String], ConfigDescriptor[KeycloakConfig]] =
    for {
      hoconFile <- ZIO.fromEither(TypesafeConfigSource.fromHoconFile(new File("src/main/resources/application.conf")))
      env       <- ConfigSource.fromSystemEnv
      sysProp   <- ConfigSource.fromSystemProperties
      source    = hoconFile <> env <> sysProp
    } yield (descriptor[KeycloakConfig] from source)

  val keycloakConfigValue: ZIO[System, String, KeycloakConfig] = for {
    desc <- keycloakConfigDescription.mapError(_.prettyPrint())
    configValue <- ZIO.fromEither(read(desc)).mapError(_.prettyPrint())
  } yield configValue
}

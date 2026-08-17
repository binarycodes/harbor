variable "REGISTRY" { default = "docker.io" }
variable "NAMESPACE" { default = "binarycodes" }
# APP_NAME must match the Maven artifactId: the Dockerfile copies the jar as
# target/${APP_NAME}-${APP_VERSION}.jar. CI overrides it from project.artifactId,
# so this default only applies to a local `docker buildx bake`.
variable "APP_NAME" { default = "harbor" }
variable "APP_VERSION" { default = "0.0.0-SNAPSHOT" }

# The published image name. Nothing overrides this, in CI or otherwise.
variable "TAG_NAME" { default = "harbor" }
variable "GIT_SHA" { default = "" }

group "default" {
  targets = ["app"]
}

target "app" {
  context    = "."
  dockerfile = "Dockerfile"

  args = {
    APP_NAME    = APP_NAME
    APP_VERSION = APP_VERSION
    GIT_SHA     = GIT_SHA
  }

  tags = [
    "${REGISTRY}/${NAMESPACE}/${TAG_NAME}:${APP_VERSION}",
    "${REGISTRY}/${NAMESPACE}/${TAG_NAME}:latest",
  ]

  platforms = ["linux/amd64", "linux/arm64"]
}
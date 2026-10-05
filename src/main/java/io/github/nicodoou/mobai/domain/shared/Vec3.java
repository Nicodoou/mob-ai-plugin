package io.github.nicodoou.mobai.domain.shared;

public record Vec3(double x, double y, double z) {
  public static final Vec3 ZERO = new Vec3(0, 0, 0);

  public Vec3 {
    if (!Double.isFinite(x) || !Double.isFinite(y) || !Double.isFinite(z)) {
      throw new IllegalArgumentException(
          "Vec3 components must be finite, got (" + x + ", " + y + ", " + z + ")");
    }
  }

  public Vec3 plus(Vec3 other) {
    return new Vec3(x + other.x, y + other.y, z + other.z);
  }

  public Vec3 minus(Vec3 other) {
    return new Vec3(x - other.x, y - other.y, z - other.z);
  }

  public Vec3 times(double factor) {
    return new Vec3(x * factor, y * factor, z * factor);
  }

  public double dot(Vec3 other) {
    return x * other.x + y * other.y + z * other.z;
  }

  public double length() {
    return Math.sqrt(dot(this));
  }

  public Vec3 horizontal() {
    return new Vec3(x, 0, z);
  }

  public double distanceTo(Vec3 other) {
    return minus(other).length();
  }

  public Vec3 normalized() {
    double length = length();
    if (length == 0) {
      throw new IllegalArgumentException("cannot normalize a zero-length vector");
    }
    return times(1 / length);
  }

  public double angleDegreesTo(Vec3 other) {
    double ownLength = length();
    double otherLength = other.length();
    if (ownLength == 0 || otherLength == 0) {
      throw new IllegalArgumentException("cannot measure an angle with a zero-length vector");
    }
    double cosine = dot(other) / (ownLength * otherLength);
    cosine = Math.clamp(cosine, -1.0, 1.0);
    return Math.toDegrees(Math.acos(cosine));
  }
}

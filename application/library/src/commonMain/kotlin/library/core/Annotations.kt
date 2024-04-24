package library.core

@RequiresOptIn(message = "This is a package-private API. Not for general use.")
@Retention(AnnotationRetention.BINARY)
@Target(AnnotationTarget.CLASS, AnnotationTarget.CONSTRUCTOR, AnnotationTarget.FUNCTION, AnnotationTarget.PROPERTY)
annotation class PackagePrivate

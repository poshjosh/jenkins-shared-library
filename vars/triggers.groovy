#!/usr/bin/env groovy
/**
 * <p>https://github.com/poshjosh</p>
 * Usage:
 * <code>
 *     triggers() // Use default values
 * </code>
 */
def call() {
    // Once in every 2 hours slot between 0900 and 1600 every Monday - Friday
    pollSCM('H H(8-16)/2 * * 1-5')
}

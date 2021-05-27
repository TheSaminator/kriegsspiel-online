import kotlinx.browser.document
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch
import kotlinx.dom.addClass
import kotlinx.dom.clear
import kotlinx.dom.removeClass
import kotlinx.html.*
import kotlinx.html.dom.append
import kotlinx.html.js.onClickFunction
import org.w3c.dom.*

sealed class Popup<T> {
	protected abstract fun TagConsumer<*>.render(callback: (T) -> Unit)
	protected fun renderInto(consumer: TagConsumer<*>, callback: (T) -> Unit) {
		consumer.render(callback)
	}
	
	suspend fun display(): T {
		popupBox.clear()
		
		return awaitCallback { callback ->
			popupBox.append {
				renderInto(this) {
					hide()
					callback(it)
				}
			}
			
			show()
		}
	}
	
	companion object {
		private val popup by lazy {
			document.getElementById("popup").unsafeCast<HTMLDivElement>()
		}
		
		private val popupBox by lazy {
			popup.firstElementChild.unsafeCast<HTMLDivElement>()
		}
		
		private fun show() {
			popup.removeClass("hide")
		}
		
		private fun hide() {
			popup.addClass("hide")
		}
	}
	
	object MainMenu : Popup<GameServerSide>() {
		override fun TagConsumer<*>.render(callback: (GameServerSide) -> Unit) {
			div(classes = "button-set col") {
				a(href = "#") {
					+"Host Game"
					onClickFunction = { e ->
						e.preventDefault()
						
						callback(GameServerSide.HOST)
					}
				}
				
				a(href = "#") {
					+"Join Game"
					onClickFunction = { e ->
						e.preventDefault()
						
						callback(GameServerSide.GUEST)
					}
				}
			}
		}
	}
	
	object ChooseNameScreen : Popup<String?>() {
		override fun TagConsumer<*>.render(callback: (String?) -> Unit) {
			val nameGameId = "name-game-id"
			val nameGameErrorId = "name-game-error"
			
			p {
				+"Please enter your player name below:"
			}
			
			input(InputType.text) {
				id = nameGameId
			}
			
			p(classes = "error") {
				id = nameGameErrorId
			}
			
			div(classes = "button-set row") {
				a(href = "#") {
					+"Cancel"
					onClickFunction = { e ->
						e.preventDefault()
						
						callback(null)
					}
				}
				a(href = "#") {
					+"Continue"
					onClickFunction = { e ->
						e.preventDefault()
						val nameGameInput = document.getElementById(nameGameId).unsafeCast<HTMLInputElement>()
						
						val gameName = nameGameInput.value
						if (gameName.isBlank()) {
							val hostGameError = document.getElementById(nameGameErrorId).unsafeCast<HTMLSpanElement>()
							hostGameError.innerHTML = "You must enter a Game Name."
						} else {
							callback(gameName)
						}
					}
				}
			}
		}
	}
	
	class HostScreen(val offerId: String) : Popup<Boolean>() {
		override fun TagConsumer<*>.render(callback: (Boolean) -> Unit) {
			p {
				+"Your game ID is "
				code {
					+"${playerName!!} ($offerId)"
				}
				+". Copy it and send it to the person with whom you want to start a game. Once you have shared the ID, click Continue to wait for a connection."
			}
			div(classes = "button-set row") {
				a(href = "#") {
					+"Cancel"
					onClickFunction = { e ->
						e.preventDefault()
						
						callback(false)
					}
				}
				a(href = "#") {
					+"Continue"
					onClickFunction = { e ->
						e.preventDefault()
						
						callback(true)
					}
				}
			}
		}
	}
	
	class JoinScreen(val sessions: List<WebRTCOpenSession>) : Popup<WebRTCOpenSession?>() {
		override fun TagConsumer<*>.render(callback: (WebRTCOpenSession?) -> Unit) {
			val joinGameId = "join-game-id"
			val joinGameErrorId = "join-game-error"
			
			p {
				+"To join a game as ${playerName!!}, please select the game ID and host name from below:"
				
				select {
					id = joinGameId
					
					option {
						value = ""
					}
					
					sessions.forEach { sess ->
						option {
							value = sess.id
							+"${sess.name} (${sess.id})"
						}
					}
				}
				
				span(classes = "error") {
					id = joinGameErrorId
				}
			}
			div(classes = "button-set row") {
				a(href = "#") {
					+"Cancel"
					onClickFunction = { e ->
						e.preventDefault()
						
						callback(null)
					}
				}
				a(href = "#") {
					+"Connect"
					onClickFunction = { e ->
						e.preventDefault()
						val joinGameInput = document.getElementById(joinGameId).unsafeCast<HTMLSelectElement>()
						
						val gameId = joinGameInput.value
						if (gameId.isBlank()) {
							val joinGameError = document.getElementById(joinGameErrorId).unsafeCast<HTMLSpanElement>()
							joinGameError.innerHTML = "You must choose a Game ID"
						} else {
							callback(sessions.singleOrNull { it.id == gameId })
						}
					}
				}
			}
		}
	}
	
	class YesNoDialogue(val acceptText: String = "Accept", val rejectText: String = "Reject", val describeRequest: P.() -> Unit) : Popup<Boolean>() {
		override fun TagConsumer<*>.render(callback: (Boolean) -> Unit) {
			p {
				describeRequest()
			}
			
			div(classes = "button-set row") {
				a(href = "#") {
					+rejectText
					onClickFunction = { e ->
						e.preventDefault()
						
						callback(false)
					}
				}
				a(href = "#") {
					+acceptText
					onClickFunction = { e ->
						e.preventDefault()
						
						callback(true)
					}
				}
			}
		}
	}
	
	class NameableChoice<T>(val headerText: String, val values: List<T>, val getName: (T) -> String) : Popup<T>() {
		override fun TagConsumer<*>.render(callback: (T) -> Unit) {
			p {
				style = "text-align: center"
				+headerText
			}
			
			div(classes = "button-set col") {
				values.forEach { value ->
					a(href = "#") {
						+getName(value)
						onClickFunction = { e ->
							e.preventDefault()
							
							callback(value)
						}
					}
				}
			}
		}
	}
	
	class LoadingScreenWithResult<T>(val loadingText: String, val successText: String, val loadAction: suspend () -> T) : Popup<T>() {
		override fun TagConsumer<*>.render(callback: (T) -> Unit) {
			val nextButtonId = "next-button"
			div(classes = "button-set row") {
				a(href = "#") {
					id = nextButtonId
					+loadingText
					onClickFunction = { e ->
						e.preventDefault()
					}
				}
			}
			
			GlobalScope.launch {
				val result = loadAction()
				val nextButton = document.getElementById(nextButtonId).unsafeCast<HTMLAnchorElement>()
				
				nextButton.textContent = successText
				nextButton.onclick = { e ->
					e.preventDefault()
					
					callback(result)
				}
			}
		}
	}
	
	class LoadingScreen(val loadingText: String, val successText: String, val loadAction: suspend () -> Unit) : Popup<Unit>() {
		override fun TagConsumer<*>.render(callback: (Unit) -> Unit) {
			LoadingScreenWithResult(loadingText, successText, loadAction).renderInto(this, callback)
		}
	}
	
	class TryLoadingScreenWithResult<T : Any>(val loadingText: String, val successText: String, val failureText: String, val loadAction: suspend () -> T?) : Popup<T?>() {
		override fun TagConsumer<*>.render(callback: (T?) -> Unit) {
			val nextButtonId = "next-button"
			div(classes = "button-set row") {
				a(href = "#") {
					id = nextButtonId
					+loadingText
					onClickFunction = { e ->
						e.preventDefault()
					}
				}
			}
			
			GlobalScope.launch {
				val result = loadAction()
				val nextButton = document.getElementById(nextButtonId).unsafeCast<HTMLAnchorElement>()
				
				nextButton.textContent = if (result != null) successText else failureText
				nextButton.onclick = { e ->
					e.preventDefault()
					
					callback(result)
				}
			}
		}
	}
	
	class TryLoadingScreen(val loadingText: String, val successText: String, val failureText: String, val loadAction: suspend () -> Boolean) : Popup<Boolean>() {
		override fun TagConsumer<*>.render(callback: (Boolean) -> Unit) {
			TryLoadingScreenWithResult(loadingText, successText, failureText) {
				if (loadAction()) Unit else null
			}.renderInto(this) { result ->
				callback(result != null)
			}
		}
	}
	
	class Message(val message: String, val centerMessage: Boolean, val closeButton: String) : Popup<Unit>() {
		override fun TagConsumer<*>.render(callback: (Unit) -> Unit) {
			p {
				if (centerMessage)
					style = "text-align: center"
				
				+message
			}
			div(classes = "button-set row") {
				a(href = "#") {
					+closeButton
					onClickFunction = { e ->
						e.preventDefault()
						
						callback(Unit)
					}
				}
			}
		}
	}
	
	class UncloseableMessage(val message: String, val centerMessage: Boolean) : Popup<Nothing>() {
		override fun TagConsumer<*>.render(callback: (Nothing) -> Unit) {
			p {
				if (centerMessage)
					style = "text-align: center"
				
				+message
			}
		}
	}
}
